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
import org.peacetalk.jmcp.core.model.Content;
import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@code @JsonIgnore}-annotated bean-validation helper methods
 * (isValid / isValidTextContent / isValidImageContent) never leak into the
 * serialized JSON as derived properties. A regression here would surface as an
 * unexpected "valid"/"validTextContent"/"validImageContent" key.
 *
 * <p>Assertions inspect the parsed JSON tree with {@code has(...)} rather than
 * substring matching so that field names embedded in string values cannot mask
 * a real leak.
 */
class ValidationMethodSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testJsonRpcResponseDoesNotSerializeValidMethod() throws Exception {
        JsonRpcResponse response = JsonRpcResponse.success(1, "result");

        JsonNode node = mapper.valueToTree(response);

        // The @JsonIgnore isValid() accessor must not appear as a "valid" property.
        assertFalse(node.has("valid"), "JSON should not contain derived 'valid' field: " + node);

        // Expected fields present with exact values.
        assertEquals("2.0", node.get("jsonrpc").asText());
        assertEquals(1, node.get("id").intValue());
        assertEquals("result", node.get("result").asText());

        JsonRpcResponse deserialized = mapper.readValue(mapper.writeValueAsString(response), JsonRpcResponse.class);
        assertEquals(response.jsonrpc(), deserialized.jsonrpc());
        assertEquals(response.result(), deserialized.result());
    }

    @Test
    void testJsonRpcResponseErrorDoesNotSerializeValidMethod() throws Exception {
        JsonRpcError error = new JsonRpcError(-32600, "Invalid Request", null);
        JsonRpcResponse response = JsonRpcResponse.error(1, error);

        JsonNode node = mapper.valueToTree(response);

        assertFalse(node.has("valid"), "JSON should not contain derived 'valid' field: " + node);

        assertEquals("2.0", node.get("jsonrpc").asText());
        assertTrue(node.has("error"));
        assertEquals(-32600, node.get("error").get("code").intValue());

        JsonRpcResponse deserialized = mapper.readValue(mapper.writeValueAsString(response), JsonRpcResponse.class);
        assertEquals(response.jsonrpc(), deserialized.jsonrpc());
        assertEquals(response.error().code(), deserialized.error().code());
    }

    @Test
    void testContentDoesNotSerializeValidationMethods() throws Exception {
        Content textContent = Content.text("Hello, world!");

        JsonNode node = mapper.valueToTree(textContent);

        // The @JsonIgnore isValidTextContent()/isValidImageContent() accessors must
        // not appear as derived properties.
        assertFalse(node.has("validTextContent"), "JSON should not contain 'validTextContent' field: " + node);
        assertFalse(node.has("validImageContent"), "JSON should not contain 'validImageContent' field: " + node);

        assertEquals("text", node.get("type").asText());
        assertEquals("Hello, world!", node.get("text").asText());

        Content deserialized = mapper.readValue(mapper.writeValueAsString(textContent), Content.class);
        assertEquals(textContent, deserialized);
    }

    @Test
    void testContentImageDoesNotSerializeValidationMethods() throws Exception {
        Content imageContent = Content.image("base64data", "image/png");

        JsonNode node = mapper.valueToTree(imageContent);

        assertFalse(node.has("validTextContent"), "JSON should not contain 'validTextContent' field: " + node);
        assertFalse(node.has("validImageContent"), "JSON should not contain 'validImageContent' field: " + node);

        assertEquals("image", node.get("type").asText());
        assertEquals("base64data", node.get("data").asText());
        assertEquals("image/png", node.get("mimeType").asText());

        Content deserialized = mapper.readValue(mapper.writeValueAsString(imageContent), Content.class);
        assertEquals(imageContent, deserialized);
    }
}
