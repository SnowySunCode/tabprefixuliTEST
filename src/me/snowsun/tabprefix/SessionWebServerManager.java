package me.snowsun.tabprefix;

import org.bukkit.entity.Player;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionWebServerManager {

    private final TabPrefix plugin;
    private final Map<UUID, PlayerSessionServer> sessions = new ConcurrentHashMap<>();
    private final DBHelper db;

    // Путь к сертификату и ключу (в папке плагина)
    private final String certFileName = "mycert.crt";
    private final String keyFileName = "mykey.key";

    public SessionWebServerManager(TabPrefix plugin, DBHelper db) {
        this.plugin = plugin;
        this.db = db;
    }

    /**
     * Запускает сессию для конкретного игрока.
     * Возвращает HTTPS URL с токеном.
     */
    public String startSessionFor(Player p) {
        try {
            stopSessionFor(p.getUniqueId());

            String token = Util.randomToken(10);

            // Подготовка SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // Загружаем KeyStore из сертификата и ключа
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            FileInputStream fis = new FileInputStream(plugin.getDataFolder().toPath().resolve(certFileName).toFile());
            ks.load(fis, null); // если нужен пароль, укажи вместо null
            fis.close();

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, null); // если нужен пароль, укажи вместо null

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            // Создаём PlayerSessionServer с SSLContext
            PlayerSessionServer s = new PlayerSessionServer(plugin, p.getUniqueId(), token, db, sslContext);
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

    /** Останавливает сессию конкретного игрока */
    public void stopSessionFor(UUID uuid) {
        PlayerSessionServer s = sessions.remove(uuid);
        if (s != null) s.stop();
    }

    /** Завершает все активные сессии */
    public void shutdownAll() {
        for (PlayerSessionServer s : new ArrayList<>(sessions.values())) {
            try { s.stop(); } catch (Exception ignored) {}
        }
        sessions.clear();
    }
}
