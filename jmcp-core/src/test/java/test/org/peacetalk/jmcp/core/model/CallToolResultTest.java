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
import tools.jackson.databind.ObjectMapper;

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

