package org.peacetalk.jmcp.server;


import org.peacetalk.jmcp.core.ToolProvider;
import org.peacetalk.jmcp.core.protocol.InitializationHandler;
import org.peacetalk.jmcp.core.protocol.McpServer;
import org.peacetalk.jmcp.jdbc.JdbcToolProvider;
import org.peacetalk.jmcp.jdbc.JdbcToolsHandler;
import org.peacetalk.jmcp.transport.stdio.StdioTransport;

/**
 * Main entry point for JDBC MCP Server
 */
public class Main {

    static void main(String[] args) {
        ToolProvider toolProvider = null;
        StdioTransport transport = null;

        try {
            // Initialize JDBC tool provider (it will load its own configuration)
            toolProvider = new JdbcToolProvider();
            toolProvider.initialize();

            System.err.println("Initialized tool provider: " + toolProvider.getName());
            System.err.println("Available tools: " + toolProvider.getTools().size());

            // Setup MCP server
            McpServer mcpServer = new McpServer();

            // Register initialization handler
            mcpServer.registerHandler(new InitializationHandler());

            // Register tools handler with the tool provider
            JdbcToolsHandler toolsHandler = new JdbcToolsHandler();
            toolsHandler.registerToolProvider(toolProvider);
            mcpServer.registerHandler(toolsHandler);

            // Start stdio transport
            transport = new StdioTransport();

            System.err.println("JDBC MCP Server starting...");

            // Setup final references for shutdown hook
            final ToolProvider finalToolProvider = toolProvider;
            final StdioTransport finalTransport = transport;

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.err.println("Shutting down...");
                try {
                    finalTransport.stop();
                    finalToolProvider.shutdown();
                } catch (Exception e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }));

            transport.start(mcpServer);

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);

            // Clean up on error
            if (toolProvider != null) {
                try {
                    toolProvider.shutdown();
                } catch (Exception ex) {
                    System.err.println("Error during cleanup: " + ex.getMessage());
                    ex.printStackTrace(System.err);
                }
            }
            if (transport != null) {
                try {
                    transport.stop();
                } catch (Exception ex) {
                    System.err.println("Error stopping transport: " + ex.getMessage());
                    ex.printStackTrace(System.err);
                }
            }

            System.exit(1);
        }
    }


}

