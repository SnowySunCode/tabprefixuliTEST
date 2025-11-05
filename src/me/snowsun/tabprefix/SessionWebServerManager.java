package me.snowsun.tabprefix;

import org.bukkit.entity.Player;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts per-player PlayerSessionServer instances and tracks them.
 */
public class SessionWebServerManager {
    private final TabPrefix plugin;
    private final Map<UUID, PlayerSessionServer> sessions = new ConcurrentHashMap<>();
    private final SSLContext ssl;
    private final DBHelper db;

    public SessionWebServerManager(TabPrefix plugin, SSLContext ssl, DBHelper db) {
        this.plugin = plugin;
        this.ssl = ssl;
        this.db = db;
    }

    public String startSessionFor(Player p) {
        try {
            stopSessionFor(p.getUniqueId());
            String token = Util.randomToken(12);
            PlayerSessionServer s = new PlayerSessionServer(plugin, p.getUniqueId(), token, db, ssl);
            boolean ok = s.start();
            if (!ok) return "error://";
            sessions.put(p.getUniqueId(), s);
            String host = s.getHost();
            String scheme = s.isHttps() ? "https" : "http";
            return scheme + "://" + host + ":" + s.getPort() + "/?t=" + token;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to start session for " + p.getName() + ": " + ex.getMessage());
            return "error://";
        }
    }

    public void stopSessionFor(UUID uuid) {
        PlayerSessionServer s = sessions.remove(uuid);
        if (s != null) s.stop();
    }

    public void shutdownAll() {
        for (PlayerSessionServer s : new ArrayList<>(sessions.values())) {
            try { s.stop(); } catch (Exception ignored) {}
        }
        sessions.clear();
    }
}
