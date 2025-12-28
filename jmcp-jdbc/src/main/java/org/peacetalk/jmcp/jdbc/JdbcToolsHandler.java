package org.peacetalk.jmcp.jdbc;

import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.ToolProvider;
import org.peacetalk.jmcp.core.model.*;
import org.peacetalk.jmcp.core.protocol.McpProtocolHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP protocol handler for tools provided by ToolProviders
 */
public class JdbcToolsHandler implements McpProtocolHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ToolProvider> toolProviders;

    public JdbcToolsHandler() {
        this.toolProviders = new ArrayList<>();
    }

    public void registerToolProvider(ToolProvider provider) {
        toolProviders.add(provider);
    }

    @Override
    public boolean canHandle(String method) {
        return method.equals("tools/list") || method.equals("tools/call");
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        try {
            return switch (request.method()) {
                case "tools/list" -> handleListTools(request);
                case "tools/call" -> handleCallTool(request);
                default -> JsonRpcResponse.error(request.id(), JsonRpcError.methodNotFound(request.method()));
            };
        } catch (Exception e) {
            System.err.println("Error handling request: " + request.method());
            e.printStackTrace(System.err);
            return JsonRpcResponse.error(request.id(), JsonRpcError.internalError(e.getMessage()));
        }
    }

    private JsonRpcResponse handleListTools(JsonRpcRequest request) {
        List<org.peacetalk.jmcp.core.model.Tool> toolList = new ArrayList<>();

        for (ToolProvider provider : toolProviders) {
            for (Tool tool : provider.getTools()) {
                toolList.add(new org.peacetalk.jmcp.core.model.Tool(
                    tool.getName(),
                    tool.getDescription(),
                    tool.getInputSchema()
                ));
            }
        }

        ListToolsResult result = new ListToolsResult(toolList);
        return JsonRpcResponse.success(request.id(), result);
    }

    private JsonRpcResponse handleCallTool(JsonRpcRequest request) {
        try {
            // Parse the call tool request
            CallToolRequest callRequest = MAPPER.convertValue(request.params(), CallToolRequest.class);

            String toolName = callRequest.name();
            JsonNode arguments = callRequest.arguments() != null ?
                callRequest.arguments() : MAPPER.createObjectNode();

            // Find the tool across all providers
            Tool tool = null;
            for (ToolProvider provider : toolProviders) {
                tool = provider.getTools().stream()
                    .filter(t -> t.getName().equals(toolName))
                    .findFirst()
                    .orElse(null);
                if (tool != null) {
                    break;
                }
            }

            if (tool == null) {
                return JsonRpcResponse.error(request.id(),
                    JsonRpcError.invalidParams("Unknown tool: " + toolName));
            }

            // Execute the tool
            Object toolResult = tool.execute(arguments);

            // Convert result to JSON string for content
            String resultJson = MAPPER.writeValueAsString(toolResult);

            // Build MCP tool result with content
            CallToolResult result = CallToolResult.text(resultJson);

            return JsonRpcResponse.success(request.id(), result);

        } catch (Exception e) {
            // Return error as MCP tool result
            System.err.println("Tool execution failed: " + e.getMessage());
            e.printStackTrace(System.err);
            CallToolResult errorResult = CallToolResult.error("Tool execution failed: " + e.getMessage());
            return JsonRpcResponse.success(request.id(), errorResult);
        }
    }
}

