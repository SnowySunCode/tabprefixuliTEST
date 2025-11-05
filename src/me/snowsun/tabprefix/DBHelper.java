package me.snowsun.tabprefix;

import java.io.File;
import java.sql.*;

/**
 * Minimal DB helper for possible future use. Creates tables if missing.
 */
public class DBHelper implements AutoCloseable {
    private final Connection conn;

    public DBHelper(File file) throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        this.conn = DriverManager.getConnection(url);
        ensureTables();
    }

    private void ensureTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS assignments (uuid TEXT PRIMARY KEY, groupname TEXT, filename TEXT, char TEXT)";
        try (Statement st = conn.createStatement()) { st.execute(sql); }
    }

    public void close() throws SQLException { if (conn != null && !conn.isClosed()) conn.close(); }
}
