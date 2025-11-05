package me.snowsun.tabprefix;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Serves web UI for a single player's session (index.html from plugins/TabPrefix/web).
 * Handles POST /api/upload?t=<token>
 */
public class PlayerSessionServer {
    private final TabPrefix plugin;
    private HttpServer httpServer;
    private String token;
    private int port;
    private boolean https;
    private final Path webRoot;
    private final UUID owner;
    private final DBHelper db;
    private final SSLContext ssl;

    public PlayerSessionServer(TabPrefix plugin, UUID owner, String token, DBHelper db, SSLContext ssl) {
        this.plugin = plugin;
        this.owner = owner;
        this.token = token;
        this.db = db;
        this.ssl = ssl;
        this.webRoot = plugin.getDataFolder().toPath().resolve("web");

        boolean ok = false;
        if (ssl != null) {
            try { HttpsServer httpsSrv = HttpsServer.create(new InetSocketAddress(0), 0); httpsSrv.setHttpsConfigurator(new HttpsConfigurator(ssl)); this.httpServer = httpsSrv; ok = true; }
            catch (Exception e) { plugin.getLogger().warning("Failed to create HttpsServer: " + e.getMessage()); }
        }
        if (!ok) {
            try { this.httpServer = HttpServer.create(new InetSocketAddress(0), 0); } catch (IOException e) { plugin.getLogger().severe("Failed to create HttpServer: " + e.getMessage()); this.httpServer = null; }
        }
        this.https = (this.httpServer instanceof HttpsServer);
    }

    public boolean start() {
        if (httpServer == null) return false;
        try {
            httpServer.createContext("/", this::handleRoot);
            httpServer.createContext("/api/upload", this::handleUpload);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            InetSocketAddress addr = httpServer.getAddress();
            this.port = addr.getPort();
            if (this.token == null || this.token.isEmpty()) this.token = Util.randomToken(12);
            plugin.getLogger().info("[PlayerSessionServer] started for " + (owner!=null?owner.toString():"unknown") + " at port " + port + " (https=" + https + ")");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("PlayerSessionServer start fail: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            plugin.getLogger().info("[PlayerSessionServer] stopped for " + (owner!=null?owner.toString():"unknown"));
        }
    }

    public int getPort() { return port; }
    public boolean isHttps() { return https; }
    public String getHost() {
        try {
            String cfg = plugin.getServer().getIp();
            if (cfg != null && !cfg.isEmpty()) return cfg;
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) { return "127.0.0.1"; }
    }

    private void handleRoot(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/") || path.equals("")) {
                Path idx = webRoot.resolve("index.html");
                if (Files.exists(idx)) { sendFile(ex, idx, Files.probeContentType(idx)); }
                else {
                    byte[] b = buildFallbackHtml().getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    ex.sendResponseHeaders(200, b.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                }
                return;
            }
            Path f = webRoot.resolve(path.substring(1)).normalize();
            if (Files.exists(f) && f.startsWith(webRoot)) sendFile(ex, f, Files.probeContentType(f));
            else sendNotFound(ex);
        } catch (IOException e) { sendServerError(ex, e.getMessage()); }
    }

    private void handleUpload(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
            String query = ex.getRequestURI().getQuery();
            if (!validateTokenInQuery(query)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"invalid_token\"}"); return; }

            String body = new String(readAllBytes(ex.getRequestBody()), StandardCharsets.UTF_8);
            String group = Util.extractJsonString(body, "group");
            String filename = Util.extractJsonString(body, "filename");
            String dataUrl = Util.extractJsonString(body, "data");
            boolean animated = Util.extractJsonBoolean(body, "animated", false);
            int frameDelay = Util.extractJsonInt(body, "frameDelay", 200);
            double posX = Util.extractJsonDouble(body, "posX", 240.0);
            double posY = Util.extractJsonDouble(body, "posY", 120.0);
            double scale = Util.extractJsonDouble(body, "scale", 1.0);

            if (group == null || filename == null || dataUrl == null) { sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_fields\"}"); return; }
            String[] parts = dataUrl.split(",", 2);
            if (parts.length != 2) { sendJson(ex, 400, "{\"ok\":false,\"error\":\"bad_dataurl\"}"); return; }
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(parts[1]); } catch (IllegalArgumentException e) { sendJson(ex, 400, "{\"ok\":false,\"error\":\"base64_decode_failed\"}"); return; }

            String code = plugin.getPhotoManager().createPendingFromUpload(group, filename, bytes, animated, frameDelay, posX, posY, scale);
            if (code == null) sendJson(ex, 500, "{\"ok\":false,\"error\":\"store_failed\"}");
            else sendJson(ex, 200, "{\"ok\":true,\"code\":\"" + code + "\"}");
        } catch (Exception e) { plugin.getLogger().warning("handleUpload error: " + e.getMessage()); sendServerError(ex, e.getMessage()); }
    }

    private boolean validateTokenInQuery(String query) { if (query == null) return false; return query.contains("t=" + token); }

    private void sendFile(HttpExchange ex, Path file, String contentType) throws IOException {
        if (contentType == null) contentType = "application/octet-stream";
        byte[] b = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private void sendNotFound(HttpExchange ex) {
        try { byte[] b = "404 Not Found".getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8"); ex.sendResponseHeaders(404, b.length); try (OutputStream os = ex.getResponseBody()) { os.write(b);} } catch (IOException ignored) {}
    }

    private void sendJson(HttpExchange ex, int status, String json) {
        try { byte[] b = json.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8"); ex.sendResponseHeaders(status, b.length); try (OutputStream os = ex.getResponseBody()) { os.write(b); } } catch (IOException ignored) {}
    }

    private void sendServerError(HttpExchange ex, String message) { sendJson(ex, 500, "{\"ok\":false,\"error\":\"server_error\",\"msg\":\"" + Util.escapeJson(message) + "\"}"); }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
        return baos.toByteArray();
    }

    private String buildFallbackHtml() {
        return "<!doctype html><html><head><meta charset='utf-8'><title>TabPrefix Editor</title></head><body>"
             + "<h2>TabPrefix — Local Editor</h2><p>Put web files into plugins/TabPrefix/web/</p></body></html>";
    }
}
