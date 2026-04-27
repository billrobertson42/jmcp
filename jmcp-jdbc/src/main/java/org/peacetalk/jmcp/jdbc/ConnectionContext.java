package org.peacetalk.jmcp.jdbc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.peacetalk.jmcp.jdbc.config.ConnectionConfig;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverClassManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Driver;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Connection pool with isolated classloader for driver and HikariCP
 */
public class ConnectionContext implements ConnectionSupplier {

    private static final Logger LOG = LogManager.getLogger(ConnectionContext.class);

    private final ConnectionConfig config;
    private final DataSource dataSource;
    private final JdbcDriverClassManager.DriverClassLoader classLoader;

    public ConnectionContext(ConnectionConfig config, Driver driver, JdbcDriverClassManager.DriverClassLoader classLoader) {
        this.config = config;
        this.classLoader = classLoader;

        try {
            // Load HikariCP classes through the driver classloader
            Class<?> hikariConfigClass = classLoader.loadClass("com.zaxxer.hikari.HikariConfig");
            Class<?> hikariDataSourceClass = classLoader.loadClass("com.zaxxer.hikari.HikariDataSource");

            // Create HikariConfig instance
            Object hikariConfig = hikariConfigClass.getDeclaredConstructor().newInstance();

            // Set properties using reflection
            hikariConfigClass.getMethod("setJdbcUrl", String.class).invoke(hikariConfig, config.jdbcUrl());
            hikariConfigClass.getMethod("setUsername", String.class).invoke(hikariConfig, config.username());
            hikariConfigClass.getMethod("setPassword", String.class).invoke(hikariConfig, config.password());
            hikariConfigClass.getMethod("setMaximumPoolSize", int.class).invoke(hikariConfig, 5);
            hikariConfigClass.getMethod("setMinimumIdle", int.class).invoke(hikariConfig, 0);
            hikariConfigClass.getMethod("setReadOnly", boolean.class).invoke(hikariConfig, true);
            hikariConfigClass.getMethod("setDriverClassName", String.class).invoke(hikariConfig, driver.getClass().getName());

            // Create HikariDataSource with the config
            this.dataSource = (DataSource) hikariDataSourceClass
                .getDeclaredConstructor(hikariConfigClass)
                .newInstance(hikariConfig);

        } catch (Exception e) {
            LOG.error("=== Failed to create connection pool ===");
            LOG.error("Connection ID: {}", config.id());
            LOG.error("Database Type: {}", config.databaseType());
            LOG.error("JDBC URL: {}", config.jdbcUrl());
            LOG.error("Username: {}", config.username());
            LOG.error("Password: {}", (config.password() != null ? "****" : "null"));
            LOG.error("Driver Class: {}", driver.getClass().getName());
            LOG.error("ClassLoader: {}", classLoader.getClass().getName());
            LOG.error("Max Pool Size: 5, Min Idle: 0, Read Only: true");

            LOG.error("========================================");
            throw new RuntimeException("Failed to create connection pool for " + config.id(), e);
        }
    }

    @Override
    public Connection getConnection() throws Exception {
        // DataSource.getConnection() works across classloaders since it's a standard interface
        return dataSource.getConnection();
    }

    public String getConnectionId() {
        return config.id();
    }

    public String getDatabaseType() {
        return config.databaseType();
    }

    public String getJdbcUrl() {
        return config.jdbcUrl();
    }

    public String getUsername() {
        return config.username();
    }

    public Set<String> getSchemaFilter() {
        return Collections.unmodifiableSet(new HashSet<>(config.schemaFilter()));
    }

    public void close() {

        try {
            if (classLoader != null) {
                classLoader.close();
            }
        } catch (Exception e) {
            LOG.error("Failed to close connection pool classloader", e);
        }

        // Close the DataSource using reflection (HikariDataSource implements AutoCloseable)
        try {
            if (dataSource instanceof AutoCloseable) {
                ((AutoCloseable) dataSource).close();
            }
        } catch (Exception e) {
            LOG.error("Error closing connection pool for {}: {}", config.id(), e.getMessage(), e);
        }
    }
}
