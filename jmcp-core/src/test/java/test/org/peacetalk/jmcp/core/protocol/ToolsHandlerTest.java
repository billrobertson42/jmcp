/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.org.peacetalk.jmcp.core.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.model.CallToolRequest;
import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.ToolsHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

class ToolsHandlerTest {

    private ToolsHandler handler;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        handler = new ToolsHandler();
        mapper = new ObjectMapper();
    }

    // ---- getSupportedMethods -------------------------------------------------

    @Test
    void testGetSupportedMethods() {
        Set<String> methods = handler.getSupportedMethods();
        assertEquals(Set.of("tools/list", "tools/call"), methods,
            "ToolsHandler must advertise exactly tools/list and tools/call");
    }

    // ---- tools/list ----------------------------------------------------------

    @Test
    void testListToolsWithNoProvidersReturnsEmptyList() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/list", null);
        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
        assertNotNull(response.result());

        // Whole-shape comparison: with no providers registered, the entire result
        // must be exactly {"tools":[]} — not merely "has a tools array somewhere".
        assertThatJson(mapper.writeValueAsString(response.result()))
            .isEqualTo("""
                    {"tools":[]}""");
    }

    @Test
    void testListToolsAggregatesAllProvidersWithNameDescriptionAndSchema() {
        handler.registerProvider(new MockMcpProvider("provider1", List.of("tool1", "tool2")));
        handler.registerProvider(new MockMcpProvider("provider2", List.of("tool3", "tool4")));

        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/list", null);
        JsonRpcResponse response = handler.handle(request);

        assertNull(response.error());

        assertThatJson(mapper.writeValueAsString(response.result()))
            .node("tools").isArray()
            .describedAs("all four tools from both providers must be listed")
            .hasSize(4)
            .allSatisfy(tool -> assertThatJson(tool).and(
                j -> j.isObject().containsKeys("name", "description", "inputSchema"),
                j -> j.node("inputSchema.type").isEqualTo("object")
                    .describedAs("MockTool schema type must be propagated verbatim")
            ))
            .extracting("name")
            .containsExactlyInAnyOrder("tool1", "tool2", "tool3", "tool4");
    }

    // ---- tools/call: success -------------------------------------------------

    @Test
    void testCallToolReturnsExecutionResultAsTextContent() {
        handler.registerProvider(new MockMcpProvider("test", List.of("test_tool")));

        CallToolRequest callRequest = new CallToolRequest("test_tool", mapper.createObjectNode());
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        JsonRpcResponse response = handler.handle(request);

        assertNull(response.error(), "a successful tool call must not be a JSON-RPC error");

        // ToolsHandler JSON-serializes the tool's return value into the text field.
        // MockTool returns the String "Result from test_tool", which serializes to a quoted
        // JSON string — so the actual text VALUE itself contains literal quote characters.
        // .isString() switches to a plain AssertJ string comparison for that node so json-unit
        // doesn't try to re-parse the expected value as JSON (which would strip a layer of quoting).
        assertThatJson(mapper.writeValueAsString(response.result())).and(
            j -> j.node("isError").isAbsent(),
            j -> j.node("content").isArray().hasSize(1),
            j -> j.node("content[0].type").isEqualTo("text"),
            j -> j.node("content[0].text").isString().isEqualTo("\"Result from test_tool\"")
        );
    }

    @Test
    void testCallToolWithNullArgumentsStillExecutes() {
        // When arguments are omitted, ToolsHandler substitutes an empty object rather
        // than passing null to the tool.
        RecordingTool recorder = new RecordingTool("recorder");
        handler.registerProvider(new MockMcpProvider("p", List.of(recorder)));

        CallToolRequest callRequest = new CallToolRequest("recorder", null);
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        JsonRpcResponse response = handler.handle(request);

        assertNull(response.error());
        assertNotNull(recorder.lastParams, "tool must be invoked even with no arguments");
        assertTrue(recorder.lastParams.isObject(),
            "null arguments must be replaced with an empty JSON object, got: " + recorder.lastParams);
        assertEquals(0, recorder.lastParams.size(), "substituted arguments object must be empty");
    }

    @Test
    void testCallToolPassesArgumentsThroughToTool() {
        RecordingTool recorder = new RecordingTool("recorder");
        handler.registerProvider(new MockMcpProvider("p", List.of(recorder)));

        var args = mapper.createObjectNode();
        args.put("key", "value");
        CallToolRequest callRequest = new CallToolRequest("recorder", args);
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        handler.handle(request);

        assertNotNull(recorder.lastParams);
        assertEquals("value", recorder.lastParams.get("key").asString(),
            "the tool must receive the exact arguments from the request");
    }

    // ---- tools/call: unknown tool -------------------------------------------

    @Test
    void testCallUnknownToolReturnsInvalidParams() {
        handler.registerProvider(new MockMcpProvider("test", List.of("tool1")));

        CallToolRequest callRequest = new CallToolRequest("unknown_tool", mapper.createObjectNode());
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        JsonRpcResponse response = handler.handle(request);

        assertNull(response.result());
        assertNotNull(response.error());
        assertEquals(JsonRpcError.INVALID_PARAMS, response.error().code(),
            "an unknown tool name must map to JSON-RPC INVALID_PARAMS (-32602)");
        assertTrue(response.error().message().contains("unknown_tool"),
            "error should name the unknown tool: " + response.error().message());
    }

    // ---- tools/call: tool execution failure ----------------------------------

    @Test
    void testCallToolThatThrowsReturnsErrorResultNotJsonRpcError() {
        // Per MCP semantics, a tool that throws yields a *successful* JSON-RPC response
        // whose result carries isError=true, so the model can see the failure text.
        handler.registerProvider(new MockMcpProvider("p", List.of(
            new ThrowingTool("boom_tool", "kaboom"))));

        CallToolRequest callRequest = new CallToolRequest("boom_tool", mapper.createObjectNode());
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/call", callRequest);

        JsonRpcResponse response = handler.handle(request);

        assertNull(response.error(),
            "a throwing tool must NOT surface as a JSON-RPC protocol error");
        assertNotNull(response.result());

        assertThatJson(mapper.writeValueAsString(response.result())).and(
            j -> j.node("isError")
                .describedAs("a throwing tool must set isError=true on the result")
                .isEqualTo(true),
            j -> j.node("content[0].text").isString()
                .describedAs("error content should surface the failure message")
                .contains("kaboom")
        );
    }

    // ---- unknown method ------------------------------------------------------

    @Test
    void testUnknownMethodReturnsMethodNotFound() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 3, "tools/unknown", null);
        JsonRpcResponse response = handler.handle(request);

        assertNull(response.result());
        assertNotNull(response.error());
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.error().code(),
            "a method this handler does not implement must map to METHOD_NOT_FOUND");
        assertTrue(response.error().message().contains("tools/unknown"),
            "error should name the offending method: " + response.error().message());
    }

    // ---- registration --------------------------------------------------------

    @Test
    void testDuplicateToolNameAcrossProvidersThrows() {
        handler.registerProvider(new MockMcpProvider("provider1", List.of("duplicate_tool")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> handler.registerProvider(new MockMcpProvider("provider2", List.of("duplicate_tool"))));

        assertTrue(exception.getMessage().contains("duplicate_tool"),
            "exception should name the conflicting tool: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("already registered"),
            "exception should explain the conflict: " + exception.getMessage());
    }

    // ---- test doubles --------------------------------------------------------

    // Mock McpProvider for testing
    private static class MockMcpProvider implements McpProvider {
        private final String name;
        private final List<Tool> tools;

        MockMcpProvider(String name, List<String> toolNames) {
            this.name = name;
            this.tools = new ArrayList<>();
            for (String toolName : toolNames) {
                tools.add(new MockTool(toolName));
            }
        }

        // Overload that accepts pre-built Tool instances.
        MockMcpProvider(String name, java.util.Collection<Tool> preBuiltTools) {
            this.name = name;
            this.tools = new ArrayList<>(preBuiltTools);
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

        MockTool(String name) {
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
            return new ObjectMapper().createObjectNode().put("type", "object");
        }

        @Override
        public Object execute(JsonNode params) {
            return "Result from " + name;
        }
    }

    // Tool that captures the arguments it was called with.
    private static class RecordingTool implements Tool {
        private final String name;
        JsonNode lastParams;

        RecordingTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Recording tool: " + name;
        }

        @Override
        public JsonNode getInputSchema() {
            return new ObjectMapper().createObjectNode().put("type", "object");
        }

        @Override
        public Object execute(JsonNode params) {
            this.lastParams = params;
            return "ok";
        }
    }

    // Tool that always fails, to exercise the error-result path.
    private static class ThrowingTool implements Tool {
        private final String name;
        private final String message;

        ThrowingTool(String name, String message) {
            this.name = name;
            this.message = message;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Throwing tool: " + name;
        }

        @Override
        public JsonNode getInputSchema() {
            return new ObjectMapper().createObjectNode().put("type", "object");
        }

        @Override
        public Object execute(JsonNode params) throws Exception {
            throw new RuntimeException(message);
        }
    }
}
