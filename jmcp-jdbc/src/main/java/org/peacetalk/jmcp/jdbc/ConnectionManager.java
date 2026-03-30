package org.peacetalk.jmcp.jdbc;

import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import javax.sql.DataSource;
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
            case "sqlite" -> "org.sqlite.JDBC";
            default -> throw new IllegalArgumentException("Unknown database type: " + databaseType);
        };
    }

    /**
     * Connection pool with isolated classloader for driver and HikariCP
     */
    private static class ConnectionPool implements ConnectionContext {
        private final String connectionId;
        private final String databaseType;
        private final String jdbcUrl;
        private final String username;
        private final DataSource dataSource;
        private final JdbcDriverManager.DriverClassLoader classLoader;

        public ConnectionPool(String connectionId, String databaseType, String jdbcUrl, String username,
                            String password, Driver driver, JdbcDriverManager.DriverClassLoader classLoader) {
            this.connectionId = connectionId;
            this.databaseType = databaseType;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.classLoader = classLoader;

            try {
                // Load HikariCP classes through the driver classloader
                Class<?> hikariConfigClass = classLoader.loadClass("com.zaxxer.hikari.HikariConfig");
                Class<?> hikariDataSourceClass = classLoader.loadClass("com.zaxxer.hikari.HikariDataSource");

                // Create HikariConfig instance
                Object config = hikariConfigClass.getDeclaredConstructor().newInstance();

                // Set properties using reflection
                hikariConfigClass.getMethod("setJdbcUrl", String.class).invoke(config, jdbcUrl);
                hikariConfigClass.getMethod("setUsername", String.class).invoke(config, username);
                hikariConfigClass.getMethod("setPassword", String.class).invoke(config, password);
                hikariConfigClass.getMethod("setMaximumPoolSize", int.class).invoke(config, 5);
                hikariConfigClass.getMethod("setMinimumIdle", int.class).invoke(config, 0);
                hikariConfigClass.getMethod("setReadOnly", boolean.class).invoke(config, true);
                hikariConfigClass.getMethod("setDriverClassName", String.class).invoke(config, driver.getClass().getName());

//                // For PostgreSQL, add connection properties to help with authentication
//                if ("postgresql".equalsIgnoreCase(databaseType)) {
//                    hikariConfigClass.getMethod("addDataSourceProperty", String.class, Object.class)
//                        .invoke(config, "ApplicationName", "jmcp-server");
//                    // Disable SSL by default for local connections (can be overridden in JDBC URL)
//                    if (!jdbcUrl.contains("ssl")) {
//                        hikariConfigClass.getMethod("addDataSourceProperty", String.class, Object.class)
//                            .invoke(config, "ssl", "false");
//                    }
//                }

                // Create HikariDataSource with the config
                this.dataSource = (DataSource) hikariDataSourceClass
                    .getDeclaredConstructor(hikariConfigClass)
                    .newInstance(config);

            } catch (Exception e) {
                System.err.println("=== Failed to create connection pool ===");
                System.err.println("Connection ID: " + connectionId);
                System.err.println("Database Type: " + databaseType);
                System.err.println("JDBC URL: " + jdbcUrl);
                System.err.println("Username: " + username);
                System.err.println("Password: " + (password != null ? "****" : "null"));
                System.err.println("Driver Class: " + driver.getClass().getName());
                System.err.println("ClassLoader: " + classLoader.getClass().getName());
                System.err.println("Max Pool Size: 5");
                System.err.println("Min Idle: 0");
                System.err.println("Read Only: true");

                // Add specific guidance for Postgres.app trust authentication errors
                if ("postgresql".equalsIgnoreCase(databaseType) &&
                    e.getMessage() != null &&
                    e.getMessage().contains("trust authentication")) {
                    System.err.println("\n*** POSTGRES.APP TRUST AUTHENTICATION ERROR ***");
                    System.err.println("Postgres.app is blocking trust authentication.");
                    System.err.println("\nPossible solutions:");
                    System.err.println("1. Add credentials to JDBC URL: jdbc:postgresql://localhost/dot?user=" + username + "&password=YOUR_PASSWORD");
                    System.err.println("2. Configure Postgres.app to allow this app in Settings > App Permissions");
                    System.err.println("3. Edit pg_hba.conf to use 'md5' or 'scram-sha-256' instead of 'trust' for localhost");
                    System.err.println("   Location: ~/Library/Application Support/Postgres/var-XX/pg_hba.conf");
                    System.err.println("   Change: 'host all all 127.0.0.1/32 trust' to 'host all all 127.0.0.1/32 md5'");
                    System.err.println("   Then restart PostgreSQL in Postgres.app");
                }

                System.err.println("========================================");
                throw new RuntimeException("Failed to create connection pool for " + connectionId, e);
            }
        }

        @Override
        public Connection getConnection() throws Exception {
            // DataSource.getConnection() works across classloaders since it's a standard interface
            return dataSource.getConnection();
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
            // Close the DataSource using reflection (HikariDataSource implements AutoCloseable)
            try {
                if (dataSource instanceof AutoCloseable) {
                    ((AutoCloseable) dataSource).close();
                }
            } catch (Exception e) {
                System.err.println("Error closing connection pool: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }
}

