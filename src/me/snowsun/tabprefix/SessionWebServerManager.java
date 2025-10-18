package me.snowsun.tabprefix;

import com.sun.net.httpserver.*;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;

public class SessionWebServerManager {

    private final TabPrefix plugin;
    private final SSLContext sslContext;
    private final DBHelper db;
    private HttpServer httpServer;
    private boolean useHttps;
    private int port;

    // active per-player sessions map
    private final Map<UUID, PlayerSessionServer> sessions = new HashMap<>();

    public SessionWebServerManager(TabPrefix plugin, SSLContext sslContext, DBHelper db) {
        this.plugin = plugin;
        this.sslContext = sslContext;
        this.db = db;
    }

    /**
     * Starts a small global info server (optional). If sslContext != null -> HTTPS, otherwise HTTP.
     */
    public boolean start() {
        try {
            if (sslContext != null) {
                HttpsServer https = HttpsServer.create(new InetSocketAddress(0), 0);
                https.setHttpsConfigurator(new HttpsConfigurator(sslContext));
                this.httpServer = https;
                this.useHttps = true;
            } else {
                this.httpServer = HttpServer.create(new InetSocketAddress(0), 0);
                this.useHttps = false;
            }

            httpServer.createContext("/", this::handleRoot);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            this.port = httpServer.getAddress().getPort();

            plugin.getLogger().info("[SessionWebServerManager] global web server started on port " + port + " (" + (useHttps ? "HTTPS" : "HTTP") + ")");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[SessionWebServerManager] failed to start global web server: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        try {
            if (httpServer != null) httpServer.stop(0);
        } catch (Exception ignored) {}
    }

    private void handleRoot(HttpExchange ex) {
        try {
            String body = "<h3>TabPrefix Session Manager</h3><p>Use plugin command to start per-player sessions.</p>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } catch (IOException ignored) {}
    }

    /**
     * Start a per-player session server and return the URL
     */
    public String startSessionFor(org.bukkit.entity.Player player) throws Exception {
        // stop old if exists
        stopSessionFor(player.getUniqueId());

        String token = Util.randomToken(12);
        PlayerSessionServer s = new PlayerSessionServer(plugin, player.getUniqueId(), token, db, sslContext);
        boolean ok = s.start();
        if (!ok) throw new IllegalStateException("Failed to start PlayerSessionServer");

        sessions.put(player.getUniqueId(), s);

        String host = InetAddress.getLocalHost().getHostAddress();
        String scheme = s.isHttps() ? "https" : "http";
        String url = scheme + "://" + host + ":" + s.getPort() + "/?t=" + token;
        plugin.getLogger().info("[SessionWebServerManager] session for " + player.getName() + " → " + url);
        return url;
    }

    public void stopSessionFor(UUID uuid) {
        PlayerSessionServer s = sessions.remove(uuid);
        if (s != null) s.stop();
    }

    public void shutdownAllSessions() {
        for (UUID u : new ArrayList<>(sessions.keySet())) {
            stopSessionFor(u);
        }
    }

    public boolean isHttps() { return useHttps; }
    public int getPort() { return port; }
}
