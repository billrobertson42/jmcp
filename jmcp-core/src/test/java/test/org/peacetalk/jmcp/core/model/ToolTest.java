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

