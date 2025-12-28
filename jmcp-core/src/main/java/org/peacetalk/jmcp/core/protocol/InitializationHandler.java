package org.peacetalk.jmcp.core.protocol;

import org.peacetalk.jmcp.core.model.*;

import java.util.Set;

/**
 * Handler for MCP initialization protocol
 */
public class InitializationHandler implements McpProtocolHandler {
    private static final String VERSION = "1.0.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";

    @Override
    public Set<String> getSupportedMethods() {
        return Set.of("initialize");
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        // Parse initialize request (optional - for validation)
        // InitializeRequest initRequest = MAPPER.convertValue(request.params(), InitializeRequest.class);

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
}

