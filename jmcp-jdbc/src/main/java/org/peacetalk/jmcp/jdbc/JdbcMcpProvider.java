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
import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.jdbc.config.ConnectionConfig;
import org.peacetalk.jmcp.jdbc.config.JdbcConfiguration;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverClassManager;
import org.peacetalk.jmcp.jdbc.resources.JdbcResourceProvider;
import org.peacetalk.jmcp.jdbc.tools.AnalyzeColumnTool;
import org.peacetalk.jmcp.jdbc.tools.ExplainQueryTool;
import org.peacetalk.jmcp.jdbc.tools.GetRowCountTool;
import org.peacetalk.jmcp.jdbc.tools.QueryTool;
import org.peacetalk.jmcp.jdbc.tools.SampleDataTool;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * McpProvider implementation for JDBC-based database tools.
 *
 * Configuration is supplied by the server via initialize(Map) using this
 * provider's JPMS module name ("org.peacetalk.jmcp.jdbc") as the config key.
 * A null config is a fatal initialization error — the server will crash with
 * a diagnostic message.
 */
public class JdbcMcpProvider implements McpProvider {
    private static final Logger LOG = LogManager.getLogger(JdbcMcpProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JdbcDriverClassManager driverManager;
    private ConnectionManager connectionManager;
    private JdbcResourceProvider resourceProvider;
    private final List<Tool> tools;

    public JdbcMcpProvider() {
        this.tools = new ArrayList<>();
    }

    @Override
    public void configure(Map<String, Object> config) throws Exception {
        if (config == null) {
            throw new IllegalStateException(
                "JDBC provider requires configuration. " +
                "Add an \"org.peacetalk.jmcp.jdbc\" section to the config file. " +
                "See config.example.json for the expected format.");
        }

        JdbcConfiguration jdbcConfig = MAPPER.convertValue(config, JdbcConfiguration.class);

        if (jdbcConfig.connections() == null || jdbcConfig.connections().length == 0) {
            throw new IllegalStateException(
                "JDBC configuration contains no connections. " +
                "At least one connection must be configured.");
        }

        // Setup driver cache directory
        Path driverCacheDir = Paths.get(System.getProperty("user.home"), ".jmcp", "drivers");
        Files.createDirectories(driverCacheDir);

        // Initialize driver manager
        driverManager = new JdbcDriverClassManager(driverCacheDir);

        // Initialize connection manager
        connectionManager = new ConnectionManager(driverManager);
        connectionManager.setDefaultConnectionId(jdbcConfig.default_id());
        connectionManager.setExposeUrls(jdbcConfig.expose_urls());

        // Register connections from config
        for (ConnectionConfig connCfg : jdbcConfig.connections()) {
            LOG.info("Registering connection: {}", connCfg.id());
            connectionManager.registerConnection(connCfg);
        }

        LOG.info("Driver cache: {}", driverCacheDir);
        LOG.info("Connections: {}", jdbcConfig.connections().length);

        // Initialize tools with adapters
        tools.clear();
        tools.add(new JdbcToolAdapter(new QueryTool(), connectionManager));
        tools.add(new JdbcToolAdapter(new ExplainQueryTool(), connectionManager));
        tools.add(new JdbcToolAdapter(new GetRowCountTool(), connectionManager));
        tools.add(new JdbcToolAdapter(new SampleDataTool(), connectionManager));
        tools.add(new JdbcToolAdapter(new AnalyzeColumnTool(), connectionManager));

        resourceProvider = new JdbcResourceProvider();
        resourceProvider.setConnectionManager(connectionManager);
        resourceProvider.initialize();
    }

    @Override
    public List<Tool> getTools() {
        return new ArrayList<>(tools);
    }

    /**
     * Get the resource provider created by this provider.
     * The resource provider shares the same connection manager.
     *
     * @return the JDBC resource provider, or null if not initialized
     */
    @Override
    public ResourceProvider getResourceProvider() {
        return resourceProvider;
    }

    @Override
    public void shutdown() {
        if (resourceProvider != null) {
            resourceProvider.shutdown();
        }
        if (connectionManager != null) {
            LOG.info("Closing all database connections...");
            connectionManager.closeAll();
        }
    }

    @Override
    public String getName() {
        return "JDBC Database Tools";
    }
}
