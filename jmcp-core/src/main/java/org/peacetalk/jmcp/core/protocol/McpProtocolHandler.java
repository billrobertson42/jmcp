package org.peacetalk.jmcp.core.protocol;

import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;

import java.util.Set;

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
     * Get the set of methods this handler supports.
     * This is called once during handler registration to build the dispatch table.
     *
     * @return Set of method names this handler can process
     */
    Set<String> getSupportedMethods();
}

