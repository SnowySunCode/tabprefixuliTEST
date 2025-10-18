package me.snowsun.tabprefix;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.ScoreboardManager;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.UUID;

import sun.security.x509.*;

public class TabPrefix extends JavaPlugin {

    private LuckPerms luckPerms;
    private ScoreboardManager scoreboardManager;
    private PhotoPrefixManager photoManager;
    private SessionWebServerManager sessionManager;
    private DBHelper db;
    private SSLContext sharedSslContext;

    private static final String KEYSTORE_NAME = "keystore.jks";
    private static final String KEYSTORE_PASSWORD = "tabprefix123";

    @Override
    public void onEnable() {
        // init DB (user already had DBHelper)
        try {
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

        // ensure plugin data folder exists
        if (!getDataFolder().exists()) {
            boolean ok = getDataFolder().mkdirs();
            if (!ok) getLogger().warning("Не удалось создать data folder: " + getDataFolder().getAbsolutePath());
        }

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
        try { sessionManager.shutdownAllSessions(); } catch (Exception ignored) {}
        try { db.close(); } catch (Exception ignored) {}
        getLogger().info("TabPrefix выключен.");
    }

    public LuckPerms getLuckPerms() { return luckPerms; }
    public PhotoPrefixManager getPhotoManager() { return photoManager; }
    public SessionWebServerManager getSessionManager() { return sessionManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public DBHelper getDB() { return db; }

    // --- update display methods (your existing logic) ---
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
            getLogger().info("[SSL] keystore not found, generating self-signed keystore: " + ksFile.getAbsolutePath());
            generateSelfSignedKeystore(ksFile, KEYSTORE_PASSWORD);
            getLogger().info("[SSL] keystore generated.");
        }

        // load as JKS
        try (InputStream is = new java.io.FileInputStream(ksFile)) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return ssl;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load keystore: " + ex.getMessage(), ex);
        }
    }

    private void generateSelfSignedKeystore(File outFile, String password) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();

        // build certificate info
        X500Name owner = new X500Name("CN=TabPrefix, OU=Local, O=SnowySun, L=Localhost, ST=None, C=US");
        long now = System.currentTimeMillis();
        Date from = new Date(now);
        Date to = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 year
        CertificateValidity interval = new CertificateValidity(from, to);
        BigInteger sn = new BigInteger(64, new SecureRandom());

        X509CertInfo info = new X509CertInfo();
        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
        info.set(X509CertInfo.SUBJECT, owner);
        info.set(X509CertInfo.ISSUER, owner);
        info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        AlgorithmId algo = new AlgorithmId(AlgorithmId.sha256WithRSAEncryption_oid);
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(pair.getPrivate(), "SHA256withRSA");

        // put into keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        Certificate[] chain = new Certificate[]{cert};
        ks.setKeyEntry("tabprefix", pair.getPrivate(), password.toCharArray(), chain);

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            ks.store(fos, password.toCharArray());
        }
    }
}
