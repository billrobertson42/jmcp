package org.peacetalk.jmcp.server;


import org.peacetalk.jmcp.core.protocol.InitializationHandler;
import org.peacetalk.jmcp.core.protocol.McpServer;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.JdbcToolsHandler;
import org.peacetalk.jmcp.jdbc.driver.JdbcDriverManager;
import org.peacetalk.jmcp.jdbc.tools.*;
import org.peacetalk.jmcp.transport.stdio.StdioTransport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main entry point for JDBC MCP Server
 */
public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        try {
            // Setup driver cache directory
            Path driverCacheDir = Paths.get(System.getProperty("user.home"), ".jmcp", "drivers");
            Files.createDirectories(driverCacheDir);

            // Initialize driver manager
            JdbcDriverManager driverManager = new JdbcDriverManager(driverCacheDir);

            // Initialize connection manager
            ConnectionManager connectionManager = new ConnectionManager(driverManager);

            // Load configuration
            Configuration config = loadConfiguration();

            // Register connections from config
            for (ConnectionConfig conn : config.connections()) {
                System.err.println("Registering connection: " + conn.id());
                connectionManager.registerConnection(
                    conn.id(),
                    conn.databaseType(),
                    conn.jdbcUrl(),
                    conn.username(),
                    conn.password()
                );
            }

            // Setup MCP server
            McpServer mcpServer = new McpServer();

            // Register initialization handler
            mcpServer.registerHandler(new InitializationHandler());

            // Register JDBC tools handler

            JdbcToolsHandler toolsHandler = new JdbcToolsHandler();
            toolsHandler.registerTool(new QueryTool());
            toolsHandler.registerTool(new ListTablesTool());
            toolsHandler.registerTool(new ListSchemasTool());
            toolsHandler.registerTool(new DescribeTableTool());
            toolsHandler.registerTool(new GetRowCountTool());
            toolsHandler.registerTool(new PreviewTableTool());

            // Register connections with tools handler
            for (ConnectionConfig conn : config.connections()) {
                toolsHandler.registerConnection(conn.id(), connectionManager.getContext(conn.id()));
            }

            mcpServer.registerHandler(toolsHandler);

            // Start stdio transport
            StdioTransport transport = new StdioTransport();

            System.err.println("JDBC MCP Server starting...");
            System.err.println("Driver cache: " + driverCacheDir);
            System.err.println("Connections: " + config.connections().length);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.err.println("Shutting down...");
                try {
                    transport.stop();
                    connectionManager.closeAll();
                } catch (Exception e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                }
            }));

            transport.start(mcpServer);

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Configuration loadConfiguration() throws IOException {
        // Try to load from config file
        Path configPath = Paths.get(System.getProperty("user.home"), ".jmcp", "config.json");

        if (Files.exists(configPath)) {
            System.err.println("Loading configuration from: " + configPath);
            JsonNode configNode = MAPPER.readTree(configPath.toFile());
            return MAPPER.treeToValue(configNode, Configuration.class);
        }

        // Try environment variable
        String configEnv = System.getenv("jmcp_CONFIG");
        if (configEnv != null) {
            System.err.println("Loading configuration from jmcp_CONFIG environment variable");
            JsonNode configNode = MAPPER.readTree(configEnv);
            return MAPPER.treeToValue(configNode, Configuration.class);
        }

        // Use default empty configuration
        System.err.println("No configuration found, using defaults");
        return new Configuration(new ConnectionConfig[0]);
    }

    public record Configuration(ConnectionConfig[] connections) {}

    public record ConnectionConfig(
        String id,
        String databaseType,
        String jdbcUrl,
        String username,
        String password
    ) {}
}

