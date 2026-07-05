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

package test.org.peacetalk.jmcp.core.model;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

// NOTE: exact-key-name/field-count checks below pin the MCP wire spec (a Java
// field rename could silently break it), NOT Jackson's ability to serialize.
class ToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCreateTool() {
        JsonNode schema = mapper.createObjectNode()
            .put("type", "object");

        Tool tool = new Tool("test-tool", "A test tool", schema);

        assertEquals("test-tool", tool.name());
        assertEquals("A test tool", tool.description());
        assertNotNull(tool.inputSchema());
    }

    @Test
    void testCreateToolWithoutDescription() {
        JsonNode schema = mapper.createObjectNode()
            .put("type", "object");

        Tool tool = new Tool("test-tool", null, schema);

        assertEquals("test-tool", tool.name());
        assertNull(tool.description());
        assertNotNull(tool.inputSchema());
    }

    @Test
    void testToolRequiresName() {
        JsonNode schema = mapper.createObjectNode()
            .put("type", "object");

        assertThrows(IllegalArgumentException.class, () ->
            new Tool(null, "description", schema));
        assertThrows(IllegalArgumentException.class, () ->
            new Tool("", "description", schema));
        assertThrows(IllegalArgumentException.class, () ->
            new Tool("  ", "description", schema));
    }

    @Test
    void testToolRequiresSchema() {
        assertThrows(IllegalArgumentException.class, () ->
            new Tool("test", "description", null));
    }

    @Test
    void testSerializesExactShape() {
        // Pins the MCP wire contract's key names (Tool's class Javadoc documents the
        // TypeScript interface these must match) — NOT nested-schema fidelity, which
        // is just JsonNode's own pass-through serialization and is covered more
        // rigorously by testRoundTripPreservesSchema's deep-equality check below.
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("x").put("type", "string");

        Tool tool = new Tool("test-tool", "A test tool", schema);

        JsonNode node = mapper.valueToTree(tool);

        assertEquals("test-tool", node.get("name").asText(), "name must serialize under key 'name'");
        assertEquals("A test tool", node.get("description").asText(),
            "description must serialize under key 'description'");
        assertTrue(node.has("inputSchema"), "schema must serialize under key 'inputSchema'");
        assertEquals(3, node.size(), "tool with description should serialize exactly name + description + inputSchema");
    }

    @Test
    void testOmitsNullDescription() {
        // description is @JsonInclude(NON_NULL) at the type level; a null description
        // must be omitted rather than written as null.
        JsonNode schema = mapper.createObjectNode().put("type", "object");
        Tool tool = new Tool("test-tool", null, schema);

        JsonNode node = mapper.valueToTree(tool);

        assertFalse(node.has("description"), "null description must be omitted (NON_NULL)");
        assertTrue(node.has("name"));
        assertTrue(node.has("inputSchema"));
        assertEquals(2, node.size(), "tool without description should serialize exactly name + inputSchema");
    }

    @Test
    void testRoundTripPreservesSchema() throws Exception {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("x");

        Tool original = new Tool("test-tool", "A test tool", schema);

        String json = mapper.writeValueAsString(original);
        Tool deserialized = mapper.readValue(json, Tool.class);

        assertEquals(original.name(), deserialized.name());
        assertEquals(original.description(), deserialized.description());
        assertEquals(original.inputSchema(), deserialized.inputSchema(),
            "inputSchema JsonNode must round-trip to an equal tree");
    }

    @Test
    void testRoundTripWithoutDescription() throws Exception {
        JsonNode schema = mapper.createObjectNode().put("type", "object");
        Tool original = new Tool("test-tool", null, schema);

        String json = mapper.writeValueAsString(original);
        Tool deserialized = mapper.readValue(json, Tool.class);

        assertEquals(original.name(), deserialized.name());
        assertNull(deserialized.description(), "absent description must deserialize back to null");
        assertEquals(original.inputSchema(), deserialized.inputSchema());
    }
}
