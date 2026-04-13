package org.peacetalk.jmcp.core.protocol;

import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.model.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic MCP protocol handler for tools provided by McpProviders.
 * This handler aggregates tools from multiple providers and handles
 * tools/list and tools/call requests.
 *
 * Uses O(1) HashMap lookup for tool dispatch by building an index
 * during provider registration.
 */
public class ToolsHandler implements McpProtocolHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<McpProvider> toolProviders;
    private final Map<String, Tool> toolIndex;

    public ToolsHandler() {
        this.toolProviders = new ArrayList<>();
        this.toolIndex = new HashMap<>();
    }

    /**
     * Register a provider with this handler.
     * All tools from the provider will be available through this handler.
     * Builds an index of tool names for O(1) lookup during tool calls.
     *
     * @param provider The provider to register
     * @throws IllegalStateException if a tool with duplicate name is registered
     */
    public void registerProvider(McpProvider provider) {
        toolProviders.add(provider);

        // Build index for O(1) tool lookup
        for (Tool tool : provider.getTools()) {
            String toolName = tool.getName();
            if (toolIndex.containsKey(toolName)) {
                throw new IllegalStateException(
                    "Tool '" + toolName + "' is already registered. " +
                    "Tool names must be unique across all providers."
                );
            }
            toolIndex.put(toolName, tool);
        }
    }

    @Override
    public Set<String> getSupportedMethods() {
        return Set.of("tools/list", "tools/call");
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

        // Aggregate tools from all registered providers
        for (McpProvider provider : toolProviders) {
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

            // O(1) lookup in the tool index
            Tool tool = toolIndex.get(toolName);

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

