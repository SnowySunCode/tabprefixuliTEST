package me.snowsun.tabprefix;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.ScoreboardManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Main plugin class.
 * - prepares DB
 * - tries to prepare SSL (via keytool-generated keystore.jks)
 * - starts ResourcePackServer
 * - starts SessionWebServerManager
 */
public class TabPrefix extends JavaPlugin {

    public static final String KEYSTORE_NAME = "keystore.jks";
    public static final String KEYSTORE_PASSWORD = "tabprefix123";
    public static final String PACK_NAME = "pack.zip";

    private LuckPerms luckPerms;
    private ScoreboardManager scoreboardManager;
    private PhotoPrefixManager photoManager;
    private SessionWebServerManager sessionManager;
    private DBHelper db;
    private ResourcePackServer packServer;
    private SSLContext sharedSsl;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        // DB
        try {
            this.db = new DBHelper(new File(getDataFolder(), "tabprefix.db"));
        } catch (Exception ex) {
            getLogger().severe("Не удалось инициализировать БД: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // LuckPerms
        try {
            this.luckPerms = LuckPermsProvider.get();
        } catch (Exception ex) {
            getLogger().severe("LuckPerms API не найден. Установите LuckPerms и перезапустите сервер.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.photoManager = new PhotoPrefixManager(this);

        // prepare SSL (keystore generation via keytool if possible)
        try {
            this.sharedSsl = prepareSSLContext();
            if (this.sharedSsl != null) getLogger().info("SSL prepared.");
        } catch (Exception e) {
            this.sharedSsl = null;
            getLogger().warning("SSL not prepared: " + e.getMessage());
        }

        // Resource pack server
        this.packServer = new ResourcePackServer(this, sharedSsl);
        this.packServer.start();

        // session manager
        this.sessionManager = new SessionWebServerManager(this, sharedSsl, db);

        // listeners & commands
        getServer().getPluginManager().registerEvents(new TabListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        new LuckPermsListener(this);

        if (getCommand("lptab") != null) getCommand("lptab").setExecutor(new TabCommand(this));
        if (getCommand("certhelp") != null) getCommand("certhelp").setExecutor(new CertHelpCommand(this));

        // schedule animations tick
        Bukkit.getScheduler().runTaskTimer(this, () -> photoManager.tickAnimations(), 1L, 4L);

        // initial update
        Bukkit.getScheduler().runTask(this, this::updateAllPlayers);

        getLogger().info("TabPrefix enabled.");
    }

    @Override
    public void onDisable() {
        try { sessionManager.shutdownAll(); } catch (Exception ignored) {}
        try { packServer.stop(); } catch (Exception ignored) {}
        try { db.close(); } catch (Exception ignored) {}
        getLogger().info("TabPrefix disabled.");
    }

    public LuckPerms getLuckPerms() { return luckPerms; }
    public PhotoPrefixManager getPhotoManager() { return photoManager; }
    public SessionWebServerManager getSessionManager() { return sessionManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public DBHelper getDB() { return db; }
    public ResourcePackServer getPackServer() { return packServer; }

    public void updatePlayerDisplay(org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline()) return;

        String prefix = "";
        String suffix = "";
        try {
            net.luckperms.api.model.user.User u = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (u != null) {
                if (u.getCachedData().getMetaData().getPrefix() != null) prefix = u.getCachedData().getMetaData().getPrefix();
                if (u.getCachedData().getMetaData().getSuffix() != null) suffix = u.getCachedData().getMetaData().getSuffix();
            }
        } catch (Exception ignore) {}

        PhotoPrefixManager.PhotoAssignment ass = photoManager.getAssignmentForPlayer(player);
        String photoText = (ass != null) ? ass.getCurrentDisplayText() : "";

        prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = org.bukkit.ChatColor.translateAlternateColorCodes('&', suffix);

        String fullPrefix = (photoText == null ? "" : photoText) + (prefix == null ? "" : prefix);
        String fullForTab = fullPrefix + org.bukkit.ChatColor.RESET + player.getName() + (suffix == null ? "" : suffix);

        String playerListName = fullForTab.length() <= 16 ? fullForTab : fullForTab.substring(0, 16);
        try { player.setPlayerListName(playerListName); } catch (Throwable t) { player.setPlayerListName(player.getName()); }

        org.bukkit.scoreboard.Scoreboard board = scoreboardManager.getNewScoreboard();
        String teamName = Util.makeSafeTeamName(player.getName());
        org.bukkit.scoreboard.Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.setPrefix(fullPrefix != null ? fullPrefix : "");
        team.setSuffix(suffix != null ? suffix : "");
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
        player.setScoreboard(board);

        String display = fullPrefix + org.bukkit.ChatColor.RESET + player.getName() + (suffix == null ? "" : suffix);
        player.setDisplayName(display);
        player.setCustomName(display);
        player.setCustomNameVisible(true);
    }

    public void updateAllPlayers() {
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) updatePlayerDisplay(p);
    }

    // prepare SSLContext by generating keystore.jks via keytool if missing (requires JDK)
    private SSLContext prepareSSLContext() {
        try {
            File ks = new File(getDataFolder(), KEYSTORE_NAME);
            if (!ks.exists()) {
                getLogger().info("Keystore missing. Attempting to generate with keytool.");
                boolean gen = generateKeystoreWithKeytool(ks, KEYSTORE_PASSWORD);
                if (!gen) {
                    getLogger().warning("Keystore generation failed or keytool absent -> HTTPS disabled.");
                    return null;
                }
            }
            try (InputStream is = new FileInputStream(ks)) {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(is, KEYSTORE_PASSWORD.toCharArray());

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keyStore);

                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
                return ssl;
            }
        } catch (Throwable t) {
            getLogger().warning("prepareSSLContext error: " + t.getMessage());
            return null;
        }
    }

    private boolean generateKeystoreWithKeytool(File outFile, String password) {
        try {
            String javaHome = System.getProperty("java.home");
            String keytool = javaHome + File.separator + "bin" + File.separator + "keytool";
            if (System.getProperty("os.name").toLowerCase().contains("win")) keytool += ".exe";
            if (!new File(keytool).exists()) {
                getLogger().warning("keytool not found at " + keytool);
                return false;
            }
            String dname = "CN=TabPrefix, OU=Local, O=SnowySun, L=Localhost, ST=None, C=US";
            ProcessBuilder pb = new ProcessBuilder(
                    keytool,
                    "-genkeypair",
                    "-alias", "tabprefix",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-keystore", outFile.getAbsolutePath(),
                    "-storepass", password,
                    "-keypass", password,
                    "-dname", dname,
                    "-validity", "365"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                byte[] buf = new byte[8192];
                while (is.read(buf) != -1) {}
            }
            int rc = p.waitFor();
            // Export cert to keystore.crt for user convenience
            if (rc == 0) {
                try {
                    ProcessBuilder pb2 = new ProcessBuilder(
                            keytool,
                            "-exportcert",
                            "-alias", "tabprefix",
                            "-keystore", outFile.getAbsolutePath(),
                            "-storepass", password,
                            "-rfc",
                            "-file", new File(getDataFolder(), "keystore.crt").getAbsolutePath()
                    );
                    pb2.redirectErrorStream(true);
                    Process p2 = pb2.start();
                    try (InputStream is = p2.getInputStream()) { while (is.read(new byte[8192]) != -1) {} }
                    p2.waitFor();
                } catch (Throwable ignored) {}
            }
            return rc == 0 && outFile.exists();
        } catch (Throwable t) {
            getLogger().warning("generateKeystoreWithKeytool error: " + t.getMessage());
            return false;
        }
    }
}
