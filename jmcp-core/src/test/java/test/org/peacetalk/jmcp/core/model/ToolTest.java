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

import static org.junit.jupiter.api.Assertions.*;

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
    void testToolSerialization() throws Exception {
        JsonNode schema = mapper.createObjectNode()
            .put("type", "object");

        Tool tool = new Tool("test-tool", "A test tool", schema);

        String json = mapper.writeValueAsString(tool);
        assertNotNull(json);
        assertTrue(json.contains("test-tool"));
        assertTrue(json.contains("A test tool"));

        Tool deserialized = mapper.readValue(json, Tool.class);
        assertEquals(tool.name(), deserialized.name());
        assertEquals(tool.description(), deserialized.description());
    }
}

