package org.peacetalk.jmcp.server;

import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.protocol.InitializationHandler;
import org.peacetalk.jmcp.core.protocol.McpServer;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;
import org.peacetalk.jmcp.core.protocol.ToolsHandler;
import org.peacetalk.jmcp.core.transport.McpTransport;
import org.peacetalk.jmcp.core.transport.TransportProvider;
import org.peacetalk.jmcp.server.tools.ServerToolProvider;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Main entry point for the MCP Server.
 * Discovers transport and tool providers via ServiceLoader (JPMS SPI),
 * assembles the server, and starts the transport.
 */
public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        List<McpProvider> providers = new ArrayList<>();
        McpTransport transport = null;

        try {
            // 1. Load configuration
            Map<String, Object> configMap = loadConfiguration();

            // 2. Discover transport (highest priority wins)
            TransportProvider transportProvider = ServiceLoader.load(TransportProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .max(Comparator.comparingInt(TransportProvider::priority))
                .orElseThrow(() -> new IllegalStateException(
                    "No TransportProvider found on module path. " +
                    "Ensure a transport module (e.g., jmcp-transport-stdio) " +
                    "is on the module path and resolved via --add-modules."));

            System.err.println("Using transport: " + transportProvider.getName());

            // 3. Discover MCP providers
            ServiceLoader.load(McpProvider.class).forEach(providers::add);

            if (providers.isEmpty()) {
                throw new IllegalStateException(
                    "No McpProvider found on module path. " +
                    "Ensure at least one provider module (e.g., jmcp-jdbc) " +
                    "is on the module path and resolved via --add-modules.");
            }

            // 4. Initialize all providers — fail fast on any error
            for (McpProvider provider : providers) {
                // Verify provider is in a named JPMS module
                Module module = provider.getClass().getModule();
                String moduleName = module.getName();
                if (moduleName == null) {
                    throw new IllegalStateException(
                        "McpProvider " + provider.getClass().getName() +
                        " is in an unnamed module. " +
                        "All MCP providers must be in named JPMS modules.");
                }

                // Look up this provider's config section by module name
                @SuppressWarnings("unchecked")
                Map<String, Object> providerConfig = configMap != null
                    ? (Map<String, Object>) configMap.get(moduleName)
                    : null;

                System.err.println("Initializing provider: " + provider.getName() +
                    " (module: " + moduleName + ")...");
                provider.initialize(providerConfig);
                System.err.println("Initialized provider: " + provider.getName());
            }

            // 5. Assemble server
            McpServer mcpServer = assembleServer(providers);

            // 6. Start transport
            transport = transportProvider.createTransport();

            final McpTransport finalTransport = transport;
            final List<McpProvider> finalProviders = List.copyOf(providers);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.err.println("Shutting down...");
                try {
                    finalTransport.stop();
                } catch (Exception e) {
                    System.err.println("Error stopping transport: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
                for (McpProvider p : finalProviders) {
                    try {
                        p.shutdown();
                    } catch (Exception e) {
                        System.err.println("Error shutting down provider " +
                            p.getName() + ": " + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }
            }));

            System.err.println("MCP Server starting...");
            transport.start(mcpServer);
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);

            // Clean up any providers that were successfully initialized
            for (McpProvider p : providers) {
                try {
                    p.shutdown();
                } catch (Exception ex) {
                    System.err.println("Error during cleanup of " +
                        p.getName() + ": " + ex.getMessage());
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

    /**
     * Load the server configuration file.
     * The config file is a JSON object keyed by JPMS module name.
     *
     * Search order:
     * 1. System property 'jmcp.config' → file path
     * 2. ~/.jmcp/config.json
     * 3. Environment variable JMCP_CONFIG (JSON string)
     *
     * @return the parsed config map, or null if no config file is found.
     *         A null return is not an error — providers decide whether
     *         they require configuration.
     * @throws IOException if a config source is found but cannot be read/parsed
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> loadConfiguration() throws IOException {
        // Try system property first
        String configProperty = System.getProperty("jmcp.config");
        if (configProperty != null) {
            Path configPath = Paths.get(configProperty);
            if (!Files.exists(configPath)) {
                throw new IOException(
                    "Configuration file specified by system property 'jmcp.config' " +
                    "does not exist: " + configPath);
            }
            System.err.println("Loading configuration from system property: " + configPath);
            return MAPPER.readValue(configPath.toFile(), Map.class);
        }

        // Try default config file
        Path configPath = Paths.get(System.getProperty("user.home"), ".jmcp", "config.json");
        if (Files.exists(configPath)) {
            System.err.println("Loading configuration from: " + configPath);
            return MAPPER.readValue(configPath.toFile(), Map.class);
        }

        // Try environment variable
        String configEnv = System.getenv("JMCP_CONFIG");
        if (configEnv != null) {
            System.err.println("Loading configuration from JMCP_CONFIG environment variable");
            return MAPPER.readValue(configEnv, Map.class);
        }

        // No configuration found — not necessarily an error
        System.err.println("No configuration file found. " +
            "Providers that require configuration will fail during initialization.");
        return null;
    }

    /**
     * Assemble an McpServer from discovered and initialized providers.
     * Public for testability (accessible via qualified export to test module).
     *
     * @param providers initialized McpProvider instances
     * @return a fully assembled McpServer ready for transport.start()
     */
    public static McpServer assembleServer(List<McpProvider> providers) {
        McpServer server = new McpServer();

        boolean hasTools = false;
        boolean hasResources = false;

        ToolsHandler toolsHandler = new ToolsHandler();
        ResourcesHandler resourcesHandler = new ResourcesHandler();

        for (McpProvider provider : providers) {
            if (!provider.getTools().isEmpty()) {
                hasTools = true;
                toolsHandler.registerProvider(provider);
            }

            ResourceProvider rp = provider.getResourceProvider();
            if (rp != null) {
                hasResources = true;
                resourcesHandler.registerResourceProvider(rp);
            }
        }

        // Create resource proxy bridge if any resources were found
        if (hasResources) {
            ServerToolProvider proxyProvider = new ServerToolProvider(resourcesHandler);
            toolsHandler.registerProvider(proxyProvider);
            hasTools = true;
            System.err.println("Registered resource proxy tool for resource-unaware clients");
        }

        server.registerHandler(new InitializationHandler(hasTools, hasResources));
        if (hasTools) server.registerHandler(toolsHandler);
        if (hasResources) server.registerHandler(resourcesHandler);

        return server;
    }
}
