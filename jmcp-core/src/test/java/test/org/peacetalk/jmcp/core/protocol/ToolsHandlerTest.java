package test.org.peacetalk.jmcp.core.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.model.CallToolRequest;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.ToolsHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolsHandlerTest {

    private ToolsHandler handler;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        handler = new ToolsHandler();
        mapper = new ObjectMapper();
    }

    @Test
    void testGetSupportedMethods() {
        Set<String> methods = handler.getSupportedMethods();
        assertEquals(2, methods.size());
        assertTrue(methods.contains("tools/list"));
        assertTrue(methods.contains("tools/call"));
    }

    @Test
    void testListToolsWithNoProviders() throws Exception {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/list", null);
        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
        assertNotNull(response.result());
    }

    @Test
    void testListToolsWithMultipleProviders() throws Exception {
        // Create mock providers
        McpProvider provider1 = new MockMcpProvider("provider1", List.of("tool1", "tool2"));
        McpProvider provider2 = new MockMcpProvider("provider2", List.of("tool3", "tool4"));

        handler.registerProvider(provider1);
        handler.registerProvider(provider2);

        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/list", null);
        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
        // Verify all tools from both providers are listed
    }

    @Test
    void testCallToolSuccess() throws Exception {
        McpProvider provider = new MockMcpProvider("test", List.of("test_tool"));
        handler.registerProvider(provider);

        CallToolRequest callRequest = new CallToolRequest("test_tool", mapper.createObjectNode());
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
        assertNotNull(response.result());
    }

    @Test
    void testCallUnknownTool() throws Exception {
        McpProvider provider = new MockMcpProvider("test", List.of("tool1"));
        handler.registerProvider(provider);

        CallToolRequest callRequest = new CallToolRequest("unknown_tool", mapper.createObjectNode());
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("Unknown tool"));
    }

    @Test
    void testDuplicateToolNameAcrossProviders() {
        McpProvider provider1 = new MockMcpProvider("provider1", List.of("duplicate_tool"));
        McpProvider provider2 = new MockMcpProvider("provider2", List.of("duplicate_tool"));

        handler.registerProvider(provider1);

        // Should throw when registering second provider with duplicate tool name
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            handler.registerProvider(provider2);
        });

        assertTrue(exception.getMessage().contains("duplicate_tool"));
        assertTrue(exception.getMessage().contains("already registered"));
    }

    // Mock McpProvider for testing
    private static class MockMcpProvider implements McpProvider {
        private final String name;
        private final List<Tool> tools;

        public MockMcpProvider(String name, List<String> toolNames) {
            this.name = name;
            this.tools = new ArrayList<>();
            for (String toolName : toolNames) {
                tools.add(new MockTool(toolName));
            }
        }

        @Override
        public void configure(java.util.Map<String, Object> config) {}

        @Override
        public List<Tool> getTools() {
            return tools;
        }

        @Override
        public void shutdown() {}

        @Override
        public String getName() {
            return name;
        }
    }

    // Mock Tool for testing
    private static class MockTool implements Tool {
        private final String name;

        public MockTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Mock tool: " + name;
        }

        @Override
        public JsonNode getInputSchema() {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.createObjectNode().put("type", "object");
        }

        @Override
        public Object execute(JsonNode params) {
            return "Result from " + name;
        }
    }
}

