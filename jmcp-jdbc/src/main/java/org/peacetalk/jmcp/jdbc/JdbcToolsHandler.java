package org.peacetalk.jmcp.jdbc;

import org.peacetalk.jmcp.core.model.*;
import org.peacetalk.jmcp.core.protocol.McpProtocolHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP protocol handler for JDBC tools
 */
public class JdbcToolsHandler implements McpProtocolHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<JdbcTool> tools;
    private final Map<String, ConnectionContext> connections;

    public JdbcToolsHandler() {
        this.tools = new ArrayList<>();
        this.connections = new ConcurrentHashMap<>();
    }

    public void registerTool(JdbcTool tool) {
        tools.add(tool);
    }

    public void registerConnection(String connectionId, ConnectionContext context) {
        connections.put(connectionId, context);
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
            return JsonRpcResponse.error(request.id(), JsonRpcError.internalError(e.getMessage()));
        }
    }

    private JsonRpcResponse handleListTools(JsonRpcRequest request) {
        List<Tool> toolList = new ArrayList<>();

        for (JdbcTool tool : tools) {
            toolList.add(new Tool(
                tool.getName(),
                tool.getDescription(),
                tool.getInputSchema()
            ));
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

            // Find the tool
            JdbcTool tool = tools.stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst()
                .orElse(null);

            if (tool == null) {
                return JsonRpcResponse.error(request.id(),
                    JsonRpcError.invalidParams("Unknown tool: " + toolName));
            }

            // Get connection context
            String connectionId = arguments.has("connectionId") ?
                arguments.get("connectionId").asString() : "default";

            ConnectionContext context = connections.get(connectionId);
            if (context == null) {
                return JsonRpcResponse.error(request.id(),
                    JsonRpcError.invalidParams("Connection not found: " + connectionId));
            }

            // Execute the tool
            Object toolResult = tool.execute(arguments, context);

            // Convert result to JSON string for content
            String resultJson = MAPPER.writeValueAsString(toolResult);

            // Build MCP tool result with content
            CallToolResult result = CallToolResult.text(resultJson);

            return JsonRpcResponse.success(request.id(), result);

        } catch (Exception e) {
            // Return error as MCP tool result
            CallToolResult errorResult = CallToolResult.error("Tool execution failed: " + e.getMessage());
            return JsonRpcResponse.success(request.id(), errorResult);
        }
    }
}

