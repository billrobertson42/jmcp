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
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that validation methods are not serialized to JSON
 */
class ValidationMethodSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testJsonRpcResponseDoesNotSerializeValidMethod() throws Exception {
        JsonRpcResponse response = JsonRpcResponse.success(1, "result");

        String json = mapper.writeValueAsString(response);

        // Should not contain "valid" field
        assertFalse(json.contains("\"valid\""), "JSON should not contain 'valid' field: " + json);

        // Should contain expected fields
        assertTrue(json.contains("\"jsonrpc\""));
        assertTrue(json.contains("\"id\""));
        assertTrue(json.contains("\"result\""));

        // Should be able to deserialize
        JsonRpcResponse deserialized = mapper.readValue(json, JsonRpcResponse.class);
        assertEquals(response.jsonrpc(), deserialized.jsonrpc());
        assertEquals(response.id(), deserialized.id());
        assertEquals(response.result(), deserialized.result());
    }

    @Test
    void testJsonRpcResponseErrorDoesNotSerializeValidMethod() throws Exception {
        JsonRpcError error = new JsonRpcError(-32600, "Invalid Request", null);
        JsonRpcResponse response = JsonRpcResponse.error(1, error);

        String json = mapper.writeValueAsString(response);

        // Should not contain "valid" field
        assertFalse(json.contains("\"valid\""), "JSON should not contain 'valid' field: " + json);

        // Should contain expected fields
        assertTrue(json.contains("\"jsonrpc\""));
        assertTrue(json.contains("\"error\""));

        // Should be able to deserialize
        JsonRpcResponse deserialized = mapper.readValue(json, JsonRpcResponse.class);
        assertEquals(response.jsonrpc(), deserialized.jsonrpc());
        assertEquals(response.error().code(), deserialized.error().code());
    }

    @Test
    void testContentDoesNotSerializeValidationMethods() throws Exception {
        Content textContent = Content.text("Hello, world!");

        String json = mapper.writeValueAsString(textContent);

        // Should not contain validation method names
        assertFalse(json.contains("\"validTextContent\""), "JSON should not contain 'validTextContent' field: " + json);
        assertFalse(json.contains("\"validImageContent\""), "JSON should not contain 'validImageContent' field: " + json);

        // Should contain expected fields
        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"text\""));

        // Should be able to deserialize
        Content deserialized = mapper.readValue(json, Content.class);
        assertEquals(textContent.type(), deserialized.type());
        assertEquals(textContent.text(), deserialized.text());
    }

    @Test
    void testContentImageDoesNotSerializeValidationMethods() throws Exception {
        Content imageContent = Content.image("base64data", "image/png");

        String json = mapper.writeValueAsString(imageContent);

        // Should not contain validation method names
        assertFalse(json.contains("\"validTextContent\""), "JSON should not contain 'validTextContent' field: " + json);
        assertFalse(json.contains("\"validImageContent\""), "JSON should not contain 'validImageContent' field: " + json);

        // Should contain expected fields
        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"data\""));
        assertTrue(json.contains("\"mimeType\""));

        // Should be able to deserialize
        Content deserialized = mapper.readValue(json, Content.class);
        assertEquals(imageContent.type(), deserialized.type());
        assertEquals(imageContent.data(), deserialized.data());
        assertEquals(imageContent.mimeType(), deserialized.mimeType());
    }
}

