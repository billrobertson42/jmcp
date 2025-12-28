package org.peacetalk.jmcp.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.sql.Connection;
import java.sql.Driver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages JDBC connections with isolated classloaders for drivers
 */
public class ConnectionManager implements ConnectionContextResolver {

    private final JdbcDriverManager driverManager;
    private final Map<String, ConnectionPool> pools;
    private String defaultConnectionId;
    private boolean exposeUrls;

    public ConnectionManager(JdbcDriverManager driverManager) {
        this.driverManager = driverManager;
        this.pools = new ConcurrentHashMap<>();
        this.defaultConnectionId = "default";
        this.exposeUrls = false;  // Default to false for security
    }

    /**
     * Set the default connection ID
     */
    public void setDefaultConnectionId(String defaultConnectionId) {
        this.defaultConnectionId = defaultConnectionId;
    }

    /**
     * Get the default connection ID
     */
    public String getDefaultConnectionId() {
        return defaultConnectionId;
    }

    /**
     * Set whether to expose JDBC URLs in connection listings
     */
    public void setExposeUrls(boolean exposeUrls) {
        this.exposeUrls = exposeUrls;
    }

    /**
     * Get whether JDBC URLs are exposed
     */
    public boolean isExposeUrls() {
        return exposeUrls;
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
        ConnectionPool pool = new ConnectionPool(connectionId, databaseType, jdbcUrl, username, password, driver, classLoader);
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

    @Override
    public ConnectionContext getConnectionContext(String connectionId) {
        return getContext(connectionId);
    }

    /**
     * List all available connections with sanitized URLs
     */
    public List<ConnectionInfo> listConnections() {
        return pools.values().stream()
            .map(pool -> new ConnectionInfo(
                pool.getConnectionId(),
                JdbcUrlSanitizer.getExposableUrl(pool.getJdbcUrl(), exposeUrls),
                pool.getUsername(),
                pool.getDatabaseType()
            ))
            .toList();
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
        private final String databaseType;
        private final String jdbcUrl;
        private final String username;
        private final HikariDataSource dataSource;
        private final JdbcDriverManager.DriverClassLoader classLoader;

        public ConnectionPool(String connectionId, String databaseType, String jdbcUrl, String username,
                            String password, Driver driver, JdbcDriverManager.DriverClassLoader classLoader) {
            this.connectionId = connectionId;
            this.databaseType = databaseType;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
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

        public String getDatabaseType() {
            return databaseType;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void close() {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }
}

