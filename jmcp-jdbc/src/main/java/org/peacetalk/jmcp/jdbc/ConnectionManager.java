package org.peacetalk.jmcp.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;

import java.sql.Connection;
import java.sql.Driver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages JDBC connections with isolated classloaders for drivers
 */
public class ConnectionManager {

    private final JdbcDriverManager driverManager;
    private final Map<String, ConnectionPool> pools;

    public ConnectionManager(JdbcDriverManager driverManager) {
        this.driverManager = driverManager;
        this.pools = new ConcurrentHashMap<>();
    }

    /**
     * Register a new connection
     */
    public void registerConnection(String connectionId, String databaseType,
                                   String jdbcUrl, String username, String password) throws Exception {
        if (pools.containsKey(connectionId)) {
            throw new IllegalArgumentException("Connection already exists: " + connectionId);
        }

        // Load the driver in isolated classloader
        JdbcDriverManager.DriverClassLoader classLoader = driverManager.loadDriver(databaseType);

        // Determine driver class name
        String driverClassName = getDriverClassName(databaseType);
        Driver driver = classLoader.loadDriverClass(driverClassName);

        // Create connection pool
        ConnectionPool pool = new ConnectionPool(connectionId, jdbcUrl, username, password, driver, classLoader);
        pools.put(connectionId, pool);
    }

    /**
     * Get connection context for a connection ID
     */
    public ConnectionContext getContext(String connectionId) {
        ConnectionPool pool = pools.get(connectionId);
        if (pool == null) {
            throw new IllegalArgumentException("Connection not found: " + connectionId);
        }
        return pool;
    }

    /**
     * Close a connection and release resources
     */
    public void closeConnection(String connectionId) {
        ConnectionPool pool = pools.remove(connectionId);
        if (pool != null) {
            pool.close();
        }
    }

    /**
     * Close all connections
     */
    public void closeAll() {
        pools.values().forEach(ConnectionPool::close);
        pools.clear();
    }

    private String getDriverClassName(String databaseType) {
        return switch (databaseType.toLowerCase()) {
            case "postgresql" -> "org.postgresql.Driver";
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "mariadb" -> "org.mariadb.jdbc.Driver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            case "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "h2" -> "org.h2.Driver";
            case "derby" -> "org.apache.derby.jdbc.EmbeddedDriver";
            case "sqlite" -> "org.sqlite.JDBC";
            default -> throw new IllegalArgumentException("Unknown database type: " + databaseType);
        };
    }

    /**
     * Connection pool with isolated classloader
     */
    private static class ConnectionPool implements ConnectionContext {
        private final String connectionId;
        private final HikariDataSource dataSource;
        private final JdbcDriverManager.DriverClassLoader classLoader;

        public ConnectionPool(String connectionId, String jdbcUrl, String username,
                            String password, Driver driver, JdbcDriverManager.DriverClassLoader classLoader) {
            this.connectionId = connectionId;
            this.classLoader = classLoader;

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(5);
            config.setReadOnly(true); // Read-only for safety
            config.setDriverClassName(driver.getClass().getName());

            // Use the isolated classloader
            Thread currentThread = Thread.currentThread();
            ClassLoader oldClassLoader = currentThread.getContextClassLoader();
            try {
                currentThread.setContextClassLoader(classLoader);
                this.dataSource = new HikariDataSource(config);
            } finally {
                currentThread.setContextClassLoader(oldClassLoader);
            }
        }

        @Override
        public Connection getConnection() throws Exception {
            Thread currentThread = Thread.currentThread();
            ClassLoader oldClassLoader = currentThread.getContextClassLoader();
            try {
                currentThread.setContextClassLoader(classLoader);
                return dataSource.getConnection();
            } finally {
                currentThread.setContextClassLoader(oldClassLoader);
            }
        }

        public String getConnectionId() {
            return connectionId;
        }

        public void close() {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }
}

