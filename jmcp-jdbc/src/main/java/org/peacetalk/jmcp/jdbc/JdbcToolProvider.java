package org.peacetalk.jmcp.jdbc;

import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.ToolProvider;
import org.peacetalk.jmcp.jdbc.config.ConnectionConfig;
import org.peacetalk.jmcp.jdbc.config.JdbcConfiguration;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;
import org.peacetalk.jmcp.jdbc.tools.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool provider for JDBC-based database tools.
 *
 * This provider:
 * - Manages JDBC driver loading and classloader isolation
 * - Manages database connections
 * - Provides read-only database query and inspection tools
 */
public class JdbcToolProvider implements ToolProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JdbcDriverManager driverManager;
    private ConnectionManager connectionManager;
    private final List<Tool> tools;

    public JdbcToolProvider() {
        this.tools = new ArrayList<>();
    }

    @Override
    public void initialize() throws Exception {
        JdbcConfiguration jdbcConfig = loadConfiguration();

        // Setup driver cache directory
        Path driverCacheDir = Paths.get(System.getProperty("user.home"), ".jmcp", "drivers");
        Files.createDirectories(driverCacheDir);

        // Initialize driver manager
        driverManager = new JdbcDriverManager(driverCacheDir);

        // Initialize connection manager
        connectionManager = new ConnectionManager(driverManager);
        connectionManager.setDefaultConnectionId(jdbcConfig.default_id());
        connectionManager.setExposeUrls(jdbcConfig.expose_urls());

        // Register connections from config
        for (ConnectionConfig conn : jdbcConfig.connections()) {
            System.err.println("Registering connection: " + conn.id());
            connectionManager.registerConnection(
                conn.id(),
                conn.databaseType(),
                conn.jdbcUrl(),
                conn.username(),
                conn.password()
            );
        }

        System.err.println("Driver cache: " + driverCacheDir);
        System.err.println("Connections: " + jdbcConfig.connections().length);

        // Initialize tools with adapters
        tools.clear();
        tools.add(new ListConnectionsTool(connectionManager));
        tools.add(new JdbcToolAdapter(new QueryTool(), connectionManager));
        tools.add(new JdbcToolAdapter(new ListTablesTool(), connectionManager));
        tools.add(new JdbcToolAdapter(new ListSchemasTool(), connectionManager));
        tools.add(new JdbcToolAdapter(new DescribeTableTool(), connectionManager));
        tools.add(new JdbcToolAdapter(new GetRowCountTool(), connectionManager));
    }

    @Override
    public List<Tool> getTools() {
        return new ArrayList<>(tools);
    }

    @Override
    public void shutdown() {
        if (connectionManager != null) {
            System.err.println("Closing all database connections...");
            connectionManager.closeAll();
        }
    }

    @Override
    public String getName() {
        return "JDBC Database Tools";
    }

    /**
     * Load JDBC configuration from file or environment variable
     */
    private static JdbcConfiguration loadConfiguration() throws IOException {
        // Try to load from config file
        Path configPath = Paths.get(System.getProperty("user.home"), ".jmcp", "config.json");

        if (Files.exists(configPath)) {
            System.err.println("Loading configuration from: " + configPath);
            JsonNode configNode = MAPPER.readTree(configPath.toFile());
            return MAPPER.treeToValue(configNode, JdbcConfiguration.class);
        }

        // Try environment variable
        String configEnv = System.getenv("JMCP_CONFIG");
        if (configEnv != null) {
            System.err.println("Loading configuration from JMCP_CONFIG environment variable");
            JsonNode configNode = MAPPER.readTree(configEnv);
            return MAPPER.treeToValue(configNode, JdbcConfiguration.class);
        }

        // Use default empty configuration
        System.err.println("No configuration found, using defaults");
        return new JdbcConfiguration("default", false, new ConnectionConfig[0]);
    }
}
