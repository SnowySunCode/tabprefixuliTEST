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
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * TabPrefix (обновлённый)
 * Авто-генерация keystore.jks (через keytool) + экспорт certificate.cer.
 * Если keytool не доступен — падаем back to HTTP (sharedSslContext == null).
 */
public class TabPrefix extends JavaPlugin {

    private LuckPerms luckPerms;
    private ScoreboardManager scoreboardManager;
    private PhotoPrefixManager photoManager;
    private SessionWebServerManager sessionManager;
    private DBHelper db;
    private SSLContext sharedSslContext;

    private static final String KEYSTORE_NAME = "keystore.jks";
    private static final String KEYSTORE_PASSWORD = "tabprefix123";
    private static final String KEY_ALIAS = "tabprefix";

    @Override
    public void onEnable() {
        // init DB
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            this.db = new DBHelper(getDataFolder().toPath().resolve("tabprefix.db").toFile());
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

        // scoreboard & managers
        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.photoManager = new PhotoPrefixManager(this);

        // create or load shared SSLContext (auto-generates keystore.jks if needed)
        try {
            this.sharedSslContext = createOrLoadSSLContext();
            getLogger().info("SSLContext prepared (shared).");
        } catch (Exception e) {
            this.sharedSslContext = null;
            getLogger().warning("SSLContext unavailable, sessions will fall back to HTTP: " + e.getMessage());
        }

        // Session manager uses shared SSL context
        this.sessionManager = new SessionWebServerManager(this, sharedSslContext, db);

        // listeners & commands
        getServer().getPluginManager().registerEvents(new TabListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        new LuckPermsListener(this);

        if (getCommand("lptab") != null) getCommand("lptab").setExecutor(new TabCommand(this));

        // schedule animation tick (lightweight)
        Bukkit.getScheduler().runTaskTimer(this, () -> photoManager.tickAnimations(), 1L, 4L);

        // initial update
        Bukkit.getScheduler().runTask(this, this::updateAllPlayers);

        // start global session web server (informational)
        try {
            sessionManager.start();
        } catch (Exception e) {
            getLogger().warning("Не удалось запустить глобальный SessionWebServer: " + e.getMessage());
        }

        getLogger().info("TabPrefix включён (web sessions).");
    }

    @Override
    public void onDisable() {
        try { if (sessionManager != null) sessionManager.shutdownAllSessions(); } catch (Exception ignored) {}
        try { if (db != null) db.close(); } catch (Exception ignored) {}
        getLogger().info("TabPrefix выключен.");
    }

    public LuckPerms getLuckPerms() { return luckPerms; }
    public PhotoPrefixManager getPhotoManager() { return photoManager; }
    public SessionWebServerManager getSessionManager() { return sessionManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public DBHelper getDB() { return db; }

    // --- update display methods (твоя существующая логика) ---
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

    // ================= SSL helpers (create or load one shared keystore) =================
    private SSLContext createOrLoadSSLContext() throws Exception {
        File ksFile = new File(getDataFolder(), KEYSTORE_NAME);

        if (!ksFile.exists()) {
            getLogger().info("[SSL] keystore not found, attempting to generate via keytool: " + ksFile.getAbsolutePath());
            boolean gen = generateKeystoreWithKeytool(ksFile);
            if (!gen) {
                throw new IllegalStateException("keystore generation failed (keytool missing or failed).");
            }

            // try to export certificate (rfc) to certificate.cer for user's convenience
            try {
                exportCertificateRfc(ksFile, new File(getDataFolder(), "certificate.cer"));
                getLogger().info("[SSL] certificate exported to certificate.cer");
            } catch (Exception ex) {
                getLogger().warning("[SSL] failed to export certificate.cer: " + ex.getMessage());
            }
        }

        // load as JKS and create SSLContext
        try (InputStream is = new FileInputStream(ksFile)) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return ssl;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load keystore: " + ex.getMessage(), ex);
        }
    }

    /**
     * Попытка вызвать keytool для генерации self-signed keystore.
     * Возвращает true если keystore создан.
     */
    private boolean generateKeystoreWithKeytool(File keystoreFile) {
        try {
            String javaHome = System.getProperty("java.home");
            String keytoolPath = javaHome + File.separator + "bin" + File.separator + "keytool";
            File kt = new File(keytoolPath);
            if (!kt.exists() || !kt.canExecute()) {
                // fallback to system PATH
                keytoolPath = "keytool";
            }

            String dname = "CN=TabPrefix, OU=Local, O=SnowySun, L=Localhost, ST=None, C=US";

            ProcessBuilder pb = new ProcessBuilder(
                    keytoolPath,
                    "-genkeypair",
                    "-alias", KEY_ALIAS,
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "JKS",
                    "-keystore", keystoreFile.getAbsolutePath(),
                    "-storepass", KEYSTORE_PASSWORD,
                    "-keypass", KEYSTORE_PASSWORD,
                    "-dname", dname,
                    "-validity", "3650"
            );

            pb.redirectErrorStream(true);
            Process p = pb.start();

            // read output to avoid blocking
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLogger().fine("[keytool] " + line);
                }
            }

            boolean finished = p.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                getLogger().warning("[keytool] process timeout");
                return false;
            }

            int exit = p.exitValue();
            if (exit != 0) {
                getLogger().warning("[keytool] exit code " + exit);
            }
            return keystoreFile.exists();
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Error running keytool: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Экспорт сертификата из keystore в RFC PEM (через keytool -exportcert -rfc)
     */
    private void exportCertificateRfc(File keystoreFile, File outPem) throws Exception {
        String javaHome = System.getProperty("java.home");
        String keytoolPath = javaHome + File.separator + "bin" + File.separator + "keytool";
        File kt = new File(keytoolPath);
        if (!kt.exists() || !kt.canExecute()) keytoolPath = "keytool";

        ProcessBuilder pb = new ProcessBuilder(
                keytoolPath,
                "-exportcert",
                "-alias", KEY_ALIAS,
                "-keystore", keystoreFile.getAbsolutePath(),
                "-storepass", KEYSTORE_PASSWORD,
                "-rfc",
                "-file", outPem.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                getLogger().fine("[keytool-export] " + line);
            }
        }
        boolean finished = p.waitFor(10, TimeUnit.SECONDS);
        if (!finished) throw new IOException("keytool export timed out");
        if (p.exitValue() != 0) throw new IOException("keytool export failed (exit " + p.exitValue() + ")");
    }
}
