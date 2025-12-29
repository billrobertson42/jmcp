package org.peacetalk.jmcp.core.protocol;

import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.transport.McpRequestHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Main MCP server implementation that dispatches requests to protocol handlers.
 * Uses a HashMap for O(1) method dispatch instead of linear search.
 */
public class McpServer implements McpRequestHandler {
    private final ObjectMapper objectMapper;
    private final Map<String, McpProtocolHandler> methodHandlers;

    public McpServer() {
        this.objectMapper = new ObjectMapper();
        this.methodHandlers = new HashMap<>();
    }

    /**
     * Register a protocol handler. The handler's supported methods are queried
     * once and stored in a dispatch table for efficient O(1) lookup.
     *
     * @param handler The handler to register
     * @throws IllegalStateException if a method is already registered to another handler
     */
    public void registerHandler(McpProtocolHandler handler) {
        for (String method : handler.getSupportedMethods()) {
            if (methodHandlers.containsKey(method)) {
                throw new IllegalStateException(
                    "Method '" + method + "' is already registered to handler: " +
                    methodHandlers.get(method).getClass().getName()
                );
            }
            methodHandlers.put(method, handler);
        }
    }

    @Override
    public String handleRequest(String jsonRpcRequest) {
        try {
            JsonRpcRequest request = objectMapper.readValue(jsonRpcRequest, JsonRpcRequest.class);

            // Notifications have null id - process but don't send response
            if (request.id() == null) {
                processNotification(request);
                return null;
            }

            JsonRpcResponse response = processRequest(request);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            System.err.println("Failed to parse request: " + e.getMessage());
            e.printStackTrace(System.err);
            JsonRpcResponse errorResponse = JsonRpcResponse.error(
                null,
                JsonRpcError.parseError("Failed to parse request: " + e.getMessage())
            );
            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception ex) {
                System.err.println("Failed to serialize error response: " + ex.getMessage());
                ex.printStackTrace(System.err);
                return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}";
            }
        }
    }

    /**
     * Process a notification (request without id).
     * Notifications do not receive a response.
     */
    private void processNotification(JsonRpcRequest request) {
        McpProtocolHandler handler = methodHandlers.get(request.method());

        if (handler == null) {
            // Unknown notification - just log and ignore (per JSON-RPC spec)
            System.err.println("Received unknown notification: " + request.method());
            return;
        }

        try {
            handler.handle(request);
        } catch (Exception e) {
            // Log but don't return error for notifications
            System.err.println("Error handling notification '" + request.method() + "': " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private JsonRpcResponse processRequest(JsonRpcRequest request) {
        McpProtocolHandler handler = methodHandlers.get(request.method());

        if (handler == null) {
            return JsonRpcResponse.error(
                request.id(),
                JsonRpcError.methodNotFound(request.method())
            );
        }

        try {
            return handler.handle(request);
        } catch (Exception e) {
            System.err.println("Error handling request '" + request.method() + "': " + e.getMessage());
            e.printStackTrace(System.err);
            return JsonRpcResponse.error(
                request.id(),
                JsonRpcError.internalError("Error handling request: " + e.getMessage())
            );
        }
    }
}


