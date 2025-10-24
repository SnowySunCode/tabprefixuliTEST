package me.snowsun.tabprefix;

import com.sun.net.httpserver.*;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Менеджер сессий
 * Конструктор принимает SSLContext.
 */
public class SessionWebServerManager {

    private final TabPrefix plugin;
    private final SSLContext sslContext;
    private final DBHelper db;
    private final Map<UUID, PlayerSessionServer> sessions = new HashMap<>();

    private HttpServer infoServer;
    private boolean useHttps = false;

    public SessionWebServerManager(TabPrefix plugin, SSLContext sslContext, DBHelper db) {
        this.plugin = plugin;
        this.sslContext = sslContext;
        this.db = db;
    }

    public boolean start() {
        try {
            if (sslContext != null) {
                HttpsServer https = HttpsServer.create(new InetSocketAddress(0), 0);
                https.setHttpsConfigurator(new HttpsConfigurator(sslContext));
                this.infoServer = https;
                this.useHttps = true;
            } else {
                this.infoServer = HttpServer.create(new InetSocketAddress(0), 0);
                this.useHttps = false;
            }

            infoServer.createContext("/", this::handleRoot);
            infoServer.setExecutor(Executors.newCachedThreadPool());
            infoServer.start();

            plugin.getLogger().info("[SessionWebServerManager] info server started on port " + infoServer.getAddress().getPort() + " (" + (useHttps ? "HTTPS" : "HTTP") + ")");
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[SessionWebServerManager] failed to start: " + e.getMessage());
            return false;
        }
    }

    private void handleRoot(HttpExchange ex) {
        try {
            String body = "<html><body><h3>TabPrefix — Session Manager</h3><p>Use /lptab localadjust to open per-player editor (if available).</p></body></html>";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } catch (IOException ignored) {}
    }

    /**
     * Start per-player session server and return URL.
     */
    public String startSessionFor(org.bukkit.entity.Player player) throws Exception {
        stopSessionFor(player.getUniqueId());

        String token = Util.randomToken(12);
        PlayerSessionServer s = new PlayerSessionServer(plugin, player.getUniqueId(), token, db, sslContext);
        if (!s.start()) throw new IllegalStateException("Failed to start PlayerSessionServer");
        sessions.put(player.getUniqueId(), s);

        String host = InetAddress.getLocalHost().getHostAddress();
        String scheme = s.isHttps() ? "https" : "http";
        String url = scheme + "://" + host + ":" + s.getPort() + "/?t=" + token;
        plugin.getLogger().info("[SessionWebServerManager] session for " + player.getName() + " -> " + url);
        return url;
    }

    public void stopSessionFor(UUID uuid) {
        PlayerSessionServer s = sessions.remove(uuid);
        if (s != null) s.stop();
    }

    public void shutdownAllSessions() {
        for (UUID u : new ArrayList<>(sessions.keySet())) stopSessionFor(u);
        if (infoServer != null) infoServer.stop(0);
    }
}
