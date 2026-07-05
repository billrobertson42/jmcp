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
import org.peacetalk.jmcp.core.model.CallToolResult;
import org.peacetalk.jmcp.core.model.Content;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// NOTE: exact-key-name/field-count checks below pin the MCP wire spec (a Java
// field rename could silently break it), NOT Jackson's ability to serialize.
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
    void testSuccessResultSerializesExactShapeAndOmitsIsError() {
        // A successful result has isError == null, which must be omitted (NON_NULL).
        CallToolResult result = CallToolResult.text("Test data");

        JsonNode node = mapper.valueToTree(result);

        assertTrue(node.has("content"), "result must serialize the 'content' field");
        assertTrue(node.get("content").isArray(), "content must serialize as a JSON array");
        assertEquals(1, node.get("content").size());
        assertEquals("text", node.get("content").get(0).get("type").asText());
        assertEquals("Test data", node.get("content").get(0).get("text").asText());
        assertFalse(node.has("isError"), "null isError must be omitted (NON_NULL)");
        assertEquals(1, node.size(), "successful result should serialize exactly the content field");
    }

    @Test
    void testErrorResultSerializesIsErrorTrue() {
        // An error result must serialize isError=true under the exact JSON key "isError".
        CallToolResult result = CallToolResult.error("boom");

        JsonNode node = mapper.valueToTree(result);

        assertTrue(node.has("isError"), "error result must serialize the 'isError' field");
        assertTrue(node.get("isError").isBoolean(), "isError must serialize as a JSON boolean");
        assertTrue(node.get("isError").booleanValue(), "error result must serialize isError=true");
        assertEquals("boom", node.get("content").get(0).get("text").asText());
    }

    @Test
    void testEmptyContentSerializesAsEmptyArrayNotNull() {
        // content is coerced to List.of() by the compact constructor, so it must
        // serialize as [] rather than being omitted or written as null.
        CallToolResult result = new CallToolResult(null, null);

        JsonNode node = mapper.valueToTree(result);

        assertTrue(node.has("content"), "content must always be present, even when empty");
        assertTrue(node.get("content").isArray());
        assertEquals(0, node.get("content").size(), "coerced-empty content must serialize as []");
    }

    @Test
    void testSuccessResultRoundTrip() throws Exception {
        CallToolResult original = CallToolResult.text("Test data");

        String json = mapper.writeValueAsString(original);
        CallToolResult deserialized = mapper.readValue(json, CallToolResult.class);

        assertEquals(original, deserialized, "successful result must survive a JSON round-trip unchanged");
        assertNull(deserialized.isError(), "absent isError must deserialize back to null");
    }

    @Test
    void testErrorResultRoundTrip() throws Exception {
        CallToolResult original = CallToolResult.error("Error message");

        String json = mapper.writeValueAsString(original);
        CallToolResult deserialized = mapper.readValue(json, CallToolResult.class);

        assertEquals(original, deserialized, "error result must survive a JSON round-trip unchanged");
        assertTrue(deserialized.isError(), "isError=true must survive the round-trip");
    }
}
