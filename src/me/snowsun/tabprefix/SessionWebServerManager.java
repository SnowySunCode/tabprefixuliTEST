package me.snowsun.tabprefix;

import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionWebServerManager {

    private final TabPrefix plugin;
    private final Map<UUID, PlayerSessionServer> sessions = new ConcurrentHashMap<>();
    private final DBHelper db;

    public SessionWebServerManager(TabPrefix plugin, DBHelper db) {
        this.plugin = plugin;
        this.db = db;
    }

    public String startSessionFor(Player p) {
        try {
            stopSessionFor(p.getUniqueId());
            String token = Util.randomToken(10);
            PlayerSessionServer s = new PlayerSessionServer(plugin, p.getUniqueId(), token, db);
            s.start();
            sessions.put(p.getUniqueId(), s);

            String host = InetAddress.getLocalHost().getHostAddress();
            String url = "https://" + host + ":" + s.getPort() + "/?t=" + token;
            return url;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to start session for " + p.getName() + ": " + ex.getMessage());
            return "https://localhost:ERROR";
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
