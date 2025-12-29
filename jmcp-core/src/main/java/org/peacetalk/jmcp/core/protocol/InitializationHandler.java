package org.peacetalk.jmcp.core.protocol;

import org.peacetalk.jmcp.core.model.*;

import java.util.Map;
import java.util.Set;

/**
 * Handler for MCP initialization and lifecycle protocol methods.
 *
 * Handles:
 * - initialize: Initial handshake, returns server capabilities
 * - notifications/initialized: Client signals it's ready (no response)
 * - ping: Health check
 */
public class InitializationHandler implements McpProtocolHandler {
    private static final String VERSION = "1.0.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private volatile boolean initialized = false;

    @Override
    public Set<String> getSupportedMethods() {
        return Set.of("initialize", "notifications/initialized", "ping");
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        return switch (request.method()) {
            case "initialize" -> handleInitialize(request);
            case "notifications/initialized" -> handleInitialized(request);
            case "ping" -> handlePing(request);
            default -> JsonRpcResponse.error(request.id(), JsonRpcError.methodNotFound(request.method()));
        };
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        // Build server capabilities
        ServerCapabilities capabilities = new ServerCapabilities(
            null, // experimental
            null, // logging
            null, // prompts
            null, // resources
            new ServerCapabilities.ToolsCapability(false) // tools (no listChanged support yet)
        );

        // Build server info
        Implementation serverInfo = new Implementation("jmcp", VERSION);

        // Build initialize result
        InitializeResult result = new InitializeResult(
            PROTOCOL_VERSION,
            capabilities,
            serverInfo
        );

        return JsonRpcResponse.success(request.id(), result);
    }

    /**
     * Handle the initialized notification from the client.
     * This signals that the client is ready to receive requests.
     * No response is sent for notifications.
     */
    private JsonRpcResponse handleInitialized(JsonRpcRequest request) {
        initialized = true;
        System.err.println("Client initialized notification received");
        // Notifications don't get responses, but we return null which McpServer handles
        return null;
    }

    /**
     * Handle ping request for health checks.
     * Returns an empty object per MCP spec.
     */
    private JsonRpcResponse handlePing(JsonRpcRequest request) {
        return JsonRpcResponse.success(request.id(), Map.of());
    }

    /**
     * Check if the client has sent the initialized notification.
     */
    public boolean isInitialized() {
        return initialized;
    }
}

