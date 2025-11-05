package me.snowsun.tabprefix;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.Executors;

public class ResourcePackServer {
    private final TabPrefix plugin;
    private final HttpServer server;
    private final boolean https;
    private final Path pack;

    public ResourcePackServer(TabPrefix plugin, SSLContext ssl) {
        this.plugin = plugin;
        Path p = plugin.getDataFolder().toPath().resolve(TabPrefix.PACK_NAME);
        this.pack = p;
        HttpServer srv = null;
        boolean ok = false;
        if (ssl != null) {
            try {
                HttpsServer httpsSrv = HttpsServer.create(new InetSocketAddress(0), 0);
                httpsSrv.setHttpsConfigurator(new HttpsConfigurator(ssl));
                srv = httpsSrv;
                ok = true;
            } catch (Exception e) { plugin.getLogger().warning("HTTPS server init failed: " + e.getMessage()); }
        }
        if (!ok) {
            try { srv = HttpServer.create(new InetSocketAddress(0), 0); } catch (Exception e) { plugin.getLogger().severe("HTTP server init failed: " + e.getMessage()); srv = null; }
        }
        this.server = srv;
        this.https = (srv instanceof HttpsServer);
    }

    public boolean start() {
        if (server == null) return false;
        server.createContext("/pack.zip", this::handlePack);
        server.createContext("/keystore.crt", this::handleCert);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        plugin.getLogger().info("ResourcePackServer started at port " + getPort() + " (https=" + isHttps() + ")");
        return true;
    }

    public void stop() { if (server != null) server.stop(0); }

    private void handlePack(HttpExchange ex) {
        try {
            if (!Files.exists(pack)) { ex.sendResponseHeaders(404, -1); return; }
            byte[] b = Files.readAllBytes(pack);
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } catch (Exception e) { try { ex.sendResponseHeaders(500, -1);} catch (IOException ignored) {} }
    }

    private void handleCert(HttpExchange ex) {
        try {
            Path crt = plugin.getDataFolder().toPath().resolve("keystore.crt");
            if (!Files.exists(crt)) { ex.sendResponseHeaders(404, -1); return; }
            byte[] b = Files.readAllBytes(crt);
            ex.getResponseHeaders().set("Content-Type", "application/x-x509-ca-cert");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } catch (Exception e) { try { ex.sendResponseHeaders(500, -1);} catch (IOException ignored) {} }
    }

    public int getPort() {
        if (server == null) return -1;
        InetSocketAddress a = server.getAddress();
        return a.getPort();
    }

    public boolean isHttps() { return https; }

    public String getHost() {
        try {
            String cfg = plugin.getServer().getIp();
            if (cfg != null && !cfg.isEmpty()) return cfg;
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
