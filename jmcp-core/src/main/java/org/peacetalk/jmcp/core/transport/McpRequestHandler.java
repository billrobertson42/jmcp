package org.peacetalk.jmcp.core.transport;

/**
 * Handler for processing MCP JSON-RPC requests
 */
@FunctionalInterface
public interface McpRequestHandler {
    /**
     * Handle a JSON-RPC request and return a JSON-RPC response
     *
     * @param jsonRpcRequest The request as a JSON string
     * @return The response as a JSON string
     */
    String handleRequest(String jsonRpcRequest);
}

