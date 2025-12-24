package org.peacetalk.jmcp.core.protocol;

import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;

/**
 * Interface for MCP protocol handlers (tools, resources, prompts)
 */
public interface McpProtocolHandler {
    /**
     * Handle an MCP protocol request
     *
     * @param request The JSON-RPC request
     * @return The JSON-RPC response
     */
    JsonRpcResponse handle(JsonRpcRequest request);

    /**
     * Check if this handler can process the given method
     */
    boolean canHandle(String method);
}

