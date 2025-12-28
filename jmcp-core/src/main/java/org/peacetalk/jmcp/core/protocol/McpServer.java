package org.peacetalk.jmcp.core.protocol;

import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.transport.McpRequestHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main MCP server implementation that dispatches requests to protocol handlers
 */
public class McpServer implements McpRequestHandler {
    private final ObjectMapper objectMapper;
    private final List<McpProtocolHandler> handlers;

    public McpServer() {
        this.objectMapper = new ObjectMapper();
        this.handlers = new CopyOnWriteArrayList<>();
    }

    public void registerHandler(McpProtocolHandler handler) {
        handlers.add(handler);
    }

    @Override
    public String handleRequest(String jsonRpcRequest) {
        try {
            JsonRpcRequest request = objectMapper.readValue(jsonRpcRequest, JsonRpcRequest.class);
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

    private JsonRpcResponse processRequest(JsonRpcRequest request) {
        for (McpProtocolHandler handler : handlers) {
            if (handler.canHandle(request.method())) {
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

        return JsonRpcResponse.error(
            request.id(),
            JsonRpcError.methodNotFound(request.method())
        );
    }
}

