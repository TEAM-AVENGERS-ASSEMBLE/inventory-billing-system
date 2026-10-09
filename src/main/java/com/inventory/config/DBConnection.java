package com.inventory.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DBConnection - Database Connection Provider for MySQL JDBC
 * Team Member: Suraj Gusain (Backend - Servlets & JDBC)
 */
public class DBConnection {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/inventory_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "root"; // Update if local MySQL has another password

    static {
        try {
            // Load MySQL Connector/J JDBC Driver class
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found in classpath: " + e.getMessage());
        }
    }

    /**
     * Obtains a new database connection using environment variables or default settings.
     * @return java.sql.Connection
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        String url = System.getProperty("DB_URL", System.getenv().getOrDefault("DB_URL", DEFAULT_URL));
        String user = System.getProperty("DB_USER", System.getenv().getOrDefault("DB_USER", DEFAULT_USER));
        String pass = System.getProperty("DB_PASS", System.getenv().getOrDefault("DB_PASS", DEFAULT_PASS));

        return DriverManager.getConnection(url, user, pass);
    }

    /**
     * Helper to test whether the MySQL server is reachable.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("JDBC Connection test failed: " + e.getMessage());
            return false;
        }
    }
}
