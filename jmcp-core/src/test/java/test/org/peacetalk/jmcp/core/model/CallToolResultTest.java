package test.org.peacetalk.jmcp.core.model;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.CallToolResult;
import org.peacetalk.jmcp.core.model.Content;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallToolResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCreateTextResult() {
        CallToolResult result = CallToolResult.text("Result data");

        assertNotNull(result.content());
        assertEquals(1, result.content().size());
        assertEquals("text", result.content().get(0).type());
        assertEquals("Result data", result.content().get(0).text());
        assertNull(result.isError());
    }

    @Test
    void testCreateErrorResult() {
        CallToolResult result = CallToolResult.error("Error message");

        assertNotNull(result.content());
        assertEquals(1, result.content().size());
        assertEquals("text", result.content().get(0).type());
        assertEquals("Error message", result.content().get(0).text());
        assertTrue(result.isError());
    }

    @Test
    void testCreateResultWithMultipleContent() {
        List<Content> contents = List.of(
            Content.text("First"),
            Content.text("Second")
        );

        CallToolResult result = CallToolResult.of(contents);

        assertEquals(2, result.content().size());
        assertNull(result.isError());
    }

    @Test
    void testEmptyContentList() {
        CallToolResult result = new CallToolResult(null, null);

        assertNotNull(result.content());
        assertEquals(0, result.content().size());
    }

    @Test
    void testSerialization() throws Exception {
        CallToolResult result = CallToolResult.text("Test data");

        String json = mapper.writeValueAsString(result);
        assertNotNull(json);
        assertTrue(json.contains("Test data"));
        assertTrue(json.contains("content"));

        CallToolResult deserialized = mapper.readValue(json, CallToolResult.class);
        assertEquals(result.content().size(), deserialized.content().size());
    }
}

