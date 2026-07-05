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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
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

        // Node-path style (not whole-document): inputSchema's own nested content is
        // deliberately NOT pinned here (see testRoundTripPreservesSchema for that) —
        // asserting the whole document would needlessly couple this wire-contract
        // test to the nested schema's internal shape.
        assertThatJson(mapper.writeValueAsString(tool)).and(
            j -> j.node("name").isEqualTo("test-tool"),
            j -> j.node("description").isEqualTo("A test tool"),
            j -> j.node("inputSchema").isPresent(),
            j -> j.isObject().hasSize(3)
        );
    }

    @Test
    void testOmitsNullDescription() {
        // description is @JsonInclude(NON_NULL) at the type level; a null description
        // must be omitted rather than written as null.
        JsonNode schema = mapper.createObjectNode().put("type", "object");
        Tool tool = new Tool("test-tool", null, schema);

        assertThatJson(mapper.writeValueAsString(tool))
            .isEqualTo("""
                    {"name":"test-tool","inputSchema":{"type":"object"}}""");
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
