/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.peacetalk.jmcp.jdbc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.peacetalk.jmcp.jdbc.config.ConnectionConfig;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverClassManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.sql.Driver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages JDBC connections with isolated classloaders for drivers
 */
public class ConnectionManager implements ConnectionContextResolver {

    private static final Logger LOG = LogManager.getLogger(ConnectionManager.class);

    private final JdbcDriverClassManager driverManager;
    private final Map<String, ConnectionContext> pools;
    private String defaultConnectionId;
    private boolean exposeUrls;

    public ConnectionManager(JdbcDriverClassManager driverManager) {
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

     public void registerConnection(ConnectionConfig config) throws Exception {
        if (pools.containsKey(config.id())) {
            throw new IllegalArgumentException("Connection already exists: " + config.id());
        }

        // Load the driver in isolated classloader
        JdbcDriverClassManager.DriverClassLoader classLoader = driverManager.loadDriver(config.databaseType());

        // Determine driver class name
        String driverClassName = getDriverClassName(config.databaseType());
        Driver driver = classLoader.loadDriverClass(driverClassName);

        // Create connection pool
        ConnectionContext pool = new ConnectionContext(config, driver, classLoader);
        pools.put(config.id(), pool);
    }

    @Override
    public ConnectionContext getContext(String connectionId) {
        ConnectionContext pool = pools.get(connectionId);
        if (pool == null) {
            throw new IllegalArgumentException("Connection not found: " + connectionId);
        }
        return pool;
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
        ConnectionContext pool = pools.remove(connectionId);
        if (pool != null) {
            pool.close();
        }
    }

    /**
     * Close all connections
     */
    public void closeAll() {
        pools.values().forEach(ConnectionContext::close);
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

}

