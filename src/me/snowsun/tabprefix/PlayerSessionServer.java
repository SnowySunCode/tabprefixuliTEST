package me.snowsun.tabprefix;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;

public class PlayerSessionServer {

    private final TabPrefix plugin;
    private HttpServer httpServer;
    private String token;
    private int port;
    private final boolean useHttps;
    private final Path webRoot;

    private UUID ownerUuid = null;
    private DBHelper dbHelper = null;

    // ----------------- constructors -----------------

    public PlayerSessionServer(TabPrefix plugin) {
        this(plugin, null, null, null, null);
    }

    public PlayerSessionServer(TabPrefix plugin, UUID ownerUuid, String token, DBHelper dbHelper, SSLContext sslContext) {
        this.plugin = plugin;
        this.ownerUuid = ownerUuid;
        this.token = token;
        this.dbHelper = dbHelper;
        this.webRoot = plugin.getDataFolder().toPath().resolve("web");

        boolean httpsOk = false;

        if (sslContext != null) {
            try {
                HttpsServer https = HttpsServer.create(new InetSocketAddress(0), 0);
                https.setHttpsConfigurator(new HttpsConfigurator(sslContext));
                this.httpServer = https;
                httpsOk = true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to start HttpsServer: " + e.getMessage());
            }
        }

        if (!httpsOk) {
            try {
                this.httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create HttpServer: " + e.getMessage());
                this.httpServer = null;
            }
        }

        this.useHttps = (this.httpServer instanceof HttpsServer);
    }

    // ----------------- start/stop -----------------

    public boolean start() {
        if (httpServer == null) return false;
        try {
            httpServer.createContext("/", this::handleRoot);
            httpServer.createContext("/api/upload", this::handleUpload);
            httpServer.createContext("/pack.zip", this::handlePack);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();

            InetSocketAddress addr = httpServer.getAddress();
            this.port = addr.getPort();
            if (this.token == null || this.token.isEmpty()) this.token = Util.randomToken(12);
            String scheme = useHttps ? "https" : "http";
            String host = getHostAddress();
            plugin.getLogger().info("[PlayerSessionServer] started at: " + scheme + "://" + host + ":" + port + "/?t=" + token);
            plugin.getLogger().info("[PlayerSessionServer] web root: " + webRoot.toAbsolutePath().toString());
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start PlayerSessionServer: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            plugin.getLogger().info("[PlayerSessionServer] stopped");
        }
    }

    public String getToken() { return token; }
    public int getPort() { return port; }
    public boolean isHttps() { return useHttps; }

    // ----------------- utilities -----------------

    private String getHostAddress() {
        try {
            String configured = plugin.getServer().getIp();
            if (configured != null && !configured.isEmpty()) return configured;
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // ----------------- HTTP handlers -----------------

    private void handleRoot(HttpExchange ex) { /* оставляем как есть */ }

    private void handlePack(HttpExchange ex) { /* оставляем как есть */ }

    private void handleUpload(HttpExchange ex) { /* оставляем как есть */ }

    private boolean validateTokenInQuery(String query) { /* оставляем как есть */ }

    private void sendFile(HttpExchange ex, Path file, String contentType) throws IOException { /* как есть */ }

    private void sendNotFound(HttpExchange ex) { /* как есть */ }

    private void sendJson(HttpExchange ex, int status, String json) { /* как есть */ }

    private void sendServerError(HttpExchange ex, String message) { /* как есть */ }

    private static String escapeJson(String s) { /* как есть */ }

    private static byte[] readAllBytes(InputStream in) throws IOException { /* как есть */ }

    private String buildFallbackHtml() { /* как есть */ }

    // ----------------- SSL helper -----------------

    public static SSLContext createSSLContext(File keystoreFile, String password) {
        try (InputStream ksIs = new FileInputStream(keystoreFile)) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(ksIs, password.toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return ssl;
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }
}
