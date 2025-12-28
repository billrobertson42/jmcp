package test.org.peacetalk.jmcp.core.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.ToolProvider;
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
        ToolProvider provider1 = new MockToolProvider("provider1", List.of("tool1", "tool2"));
        ToolProvider provider2 = new MockToolProvider("provider2", List.of("tool3", "tool4"));

        handler.registerToolProvider(provider1);
        handler.registerToolProvider(provider2);

        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/list", null);
        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
        // Verify all tools from both providers are listed
    }

    @Test
    void testCallToolSuccess() throws Exception {
        ToolProvider provider = new MockToolProvider("test", List.of("test_tool"));
        handler.registerToolProvider(provider);

        CallToolRequest callRequest = new CallToolRequest("test_tool", mapper.createObjectNode());
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
        assertNotNull(response.result());
    }

    @Test
    void testCallUnknownTool() throws Exception {
        ToolProvider provider = new MockToolProvider("test", List.of("tool1"));
        handler.registerToolProvider(provider);

        CallToolRequest callRequest = new CallToolRequest("unknown_tool", mapper.createObjectNode());
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("Unknown tool"));
    }

    @Test
    void testDuplicateToolNameAcrossProviders() {
        ToolProvider provider1 = new MockToolProvider("provider1", List.of("duplicate_tool"));
        ToolProvider provider2 = new MockToolProvider("provider2", List.of("duplicate_tool"));

        handler.registerToolProvider(provider1);

        // Should throw when registering second provider with duplicate tool name
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            handler.registerToolProvider(provider2);
        });

        assertTrue(exception.getMessage().contains("duplicate_tool"));
        assertTrue(exception.getMessage().contains("already registered"));
    }

    // Mock ToolProvider for testing
    private static class MockToolProvider implements ToolProvider {
        private final String name;
        private final List<Tool> tools;

        public MockToolProvider(String name, List<String> toolNames) {
            this.name = name;
            this.tools = new ArrayList<>();
            for (String toolName : toolNames) {
                tools.add(new MockTool(toolName));
            }
        }

        @Override
        public void initialize() {}

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

