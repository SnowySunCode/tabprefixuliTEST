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
import java.util.concurrent.Executors;

/**
 * PlayerSessionServer
 *
 * - запускает простой (H)HTTP сервер на локальном порту
 * - отдаёт web/index.html и сопутствующие файлы из dataFolder/web/
 * - принимает POST /api/upload?t=<token> с JSON-пayload'ом
 *   { group, filename, data(dataURL), animated, frameDelay, posX, posY, scale }
 * - вызывает plugin.getPhotoManager().createPendingFromUpload(...)
 * - возвращает JSON { ok:true, code: "<approve-code>" }
 *
 * Notes:
 *  - Для HTTPS используй keystore jks в plugin.getDataFolder()/keystore.jks.
 *  - Для простоты JSON-парсинг выполнен через Util.extractJson*.
 */
public class PlayerSessionServer {

    private final TabPrefix plugin;
    private HttpServer httpServer; // can be HttpsServer instance
    private String token;
    private int port;
    private final boolean useHttps;
    private final Path webRoot;

    public PlayerSessionServer(TabPrefix plugin) {
        this.plugin = plugin;
        this.webRoot = plugin.getDataFolder().toPath().resolve("web");
        // try to init HTTPS if keystore present
        boolean httpsOk = false;
        Path ks = plugin.getDataFolder().toPath().resolve("keystore.jks");
        if (Files.exists(ks)) {
            try {
                HttpsServer https = HttpsServer.create(new InetSocketAddress(0), 0);
                SSLContext ssl = createSSLContext(ks.toFile(), "changeit"); // default password "changeit" (user may change)
                if (ssl != null) {
                    https.setHttpsConfigurator(new HttpsConfigurator(ssl));
                    this.httpServer = https;
                    httpsOk = true;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to start HttpsServer: " + e.getMessage());
                httpsOk = false;
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

    /**
     * Start server, create contexts and generate token.
     */
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
            this.token = Util.randomToken(12);
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

    private String getHostAddress() {
        try {
            // prefer server ip if available, fallback to local host
            String configured = plugin.getServer().getIp();
            if (configured != null && !configured.isEmpty()) return configured;
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // ---------------- handlers ----------------

    private void handleRoot(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.equals("")) {
                // serve index.html from webRoot if exists, otherwise fallback
                Path idx = webRoot.resolve("index.html");
                if (Files.exists(idx)) {
                    sendFile(ex, idx, Files.probeContentType(idx));
                } else {
                    // fallback built-in simple HTML
                    byte[] bytes = buildFallbackHtml().getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    ex.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
                }
                return;
            } else {
                // try to serve static file relative to webRoot
                Path file = webRoot.resolve(path.substring(1)).normalize();
                if (Files.exists(file) && file.startsWith(webRoot)) {
                    sendFile(ex, file, Files.probeContentType(file));
                } else {
                    sendNotFound(ex);
                }
            }
        } catch (IOException e) {
            sendServerError(ex, e.getMessage());
        }
    }

    private void handlePack(HttpExchange ex) {
        try {
            Path pack = plugin.getPhotoManager().getPackPath();
            if (Files.exists(pack)) {
                ex.getResponseHeaders().set("Content-Type", "application/zip");
                ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"tabprefix-pack.zip\"");
                sendFile(ex, pack, "application/zip");
            } else {
                sendJson(ex, 404, "{\"ok\":false,\"error\":\"pack_not_found\"}");
            }
        } catch (Exception e) {
            sendServerError(ex, e.getMessage());
        }
    }

    /**
     * POST /api/upload?t=<token>
     * body = JSON like:
     * { "group":"Admin", "filename":"img.png", "data":"data:image/png;base64,...", "animated":false,
     *   "frameDelay":200, "posX":240.0, "posY":120.0, "scale":1.0 }
     */
    private void handleUpload(HttpExchange ex) {
        try {
            // check method
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            // check token
            String query = ex.getRequestURI().getQuery();
            if (!validateTokenInQuery(query)) {
                sendJson(ex, 403, "{\"ok\":false,\"error\":\"invalid_token\"}");
                return;
            }

            String body = new String(readAllBytes(ex.getRequestBody()), StandardCharsets.UTF_8);

            String group = Util.extractJsonString(body, "group");
            String filename = Util.extractJsonString(body, "filename");
            String dataUrl = Util.extractJsonString(body, "data");
            boolean animated = Util.extractJsonBoolean(body, "animated", false);
            int frameDelay = Util.extractJsonInt(body, "frameDelay", 200);
            double posX = Util.extractJsonDouble(body, "posX", 240.0);
            double posY = Util.extractJsonDouble(body, "posY", 120.0);
            double scale = Util.extractJsonDouble(body, "scale", 1.0);

            if (group == null || filename == null || dataUrl == null) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_fields\"}");
                return;
            }

            // decode dataURL
            String[] parts = dataUrl.split(",", 2);
            if (parts.length != 2) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"bad_dataurl\"}");
                return;
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(parts[1]);
            } catch (IllegalArgumentException e) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"base64_decode_failed\"}");
                return;
            }

            // delegate to PhotoPrefixManager
            String code = plugin.getPhotoManager().createPendingFromUpload(group, filename, bytes, animated, frameDelay, posX, posY, scale);
            if (code == null) {
                sendJson(ex, 500, "{\"ok\":false,\"error\":\"store_failed\"}");
            } else {
                String json = "{\"ok\":true,\"code\":\"" + code + "\"}";
                sendJson(ex, 200, json);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("handleUpload error: " + e.getMessage());
            sendServerError(ex, e.getMessage());
        }
    }

    // ---------------- utilities ----------------

    private boolean validateTokenInQuery(String query) {
        if (query == null) return false;
        // simple contains check for t=<token>, allow other params
        String needle = "t=" + token;
        return query.contains(needle);
    }

    private void sendFile(HttpExchange ex, Path file, String contentType) throws IOException {
        if (contentType == null) contentType = "application/octet-stream";
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void sendNotFound(HttpExchange ex) {
        try {
            byte[] b = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } catch (IOException ignored) {}
    }

    private void sendJson(HttpExchange ex, int status, String json) {
        try {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } catch (IOException ignored) {}
    }

    private void sendServerError(HttpExchange ex, String message) {
        sendJson(ex, 500, "{\"ok\":false,\"error\":\"server_error\",\"msg\":\"" + escapeJson(message) + "\"}");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r","\\r");
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
        return baos.toByteArray();
    }

    private String buildFallbackHtml() {
        return "<!doctype html><html><head><meta charset='utf-8'><title>TabPrefix Editor</title></head><body>"
                + "<h2>TabPrefix — Local Editor</h2>"
                + "<p>No web/ folder found in plugin data directory. Place your web files into <code>plugins/TabPrefix/web/</code></p>"
                + "<p>Or use the built-in frontend shipped in your plugin build.</p>"
                + "</body></html>";
    }

    // ---------------- SSL helper ----------------

    /**
     * Create SSLContext using given keystore (JKS).
     * Password is the keystore password; default "changeit" often used.
     */
    private SSLContext createSSLContext(File keystoreFile, String password) {
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
            plugin.getLogger().warning("Failed to create SSLContext: " + e.getMessage());
            return null;
        }
    }
}
