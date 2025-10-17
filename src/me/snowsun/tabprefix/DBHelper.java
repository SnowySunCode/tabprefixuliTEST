package me.snowsun.tabprefix;

import java.io.File;
import java.sql.*;

public class DBHelper implements AutoCloseable {

    private final Connection conn;

    public DBHelper(File dbFile) throws Exception {
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        conn = DriverManager.getConnection(url);
        init();
    }

    private void init() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS sessions (player_uuid TEXT PRIMARY KEY, token TEXT, expiry INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS pending (code TEXT PRIMARY KEY, yaml TEXT, created INTEGER)");
        }
    }

    public void insertSession(String uuid, String token, long expiry) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO sessions (player_uuid, token, expiry) VALUES (?,?,?)")) {
            ps.setString(1, uuid);
            ps.setString(2, token);
            ps.setLong(3, expiry);
            ps.executeUpdate();
        }
    }

    public void extendSession(String uuid, long expiry) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE sessions SET expiry = ? WHERE player_uuid = ?")) {
            ps.setLong(1, expiry);
            ps.setString(2, uuid);
            ps.executeUpdate();
        }
    }

    public void deleteSession(String uuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE player_uuid = ?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        }
    }

    public void close() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }
}
