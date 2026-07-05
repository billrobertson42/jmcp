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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@code @JsonIgnore}-annotated bean-validation helper methods
 * (isValid / isValidTextContent / isValidImageContent) never leak into the
 * serialized JSON as derived properties. A regression here would surface as an
 * unexpected "valid"/"validTextContent"/"validImageContent" key.
 *
 * <p>Each test asserts the WHOLE serialized document against an exact expected
 * shape, rather than checking individual fields plus a separate has("valid")
 * negative check — a leaked derived property would fail the whole-document
 * comparison on its own, since it introduces a key the expected shape doesn't have.
 */
class ValidationMethodSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testJsonRpcResponseDoesNotSerializeValidMethod() throws Exception {
        JsonRpcResponse response = JsonRpcResponse.success(1, "result");

        assertThatJson(mapper.writeValueAsString(response))
            .isEqualTo("""
                    {"jsonrpc":"2.0","id":1,"result":"result"}""");

        JsonRpcResponse deserialized = mapper.readValue(mapper.writeValueAsString(response), JsonRpcResponse.class);
        assertEquals(response.jsonrpc(), deserialized.jsonrpc());
        assertEquals(response.result(), deserialized.result());
    }

    @Test
    void testJsonRpcResponseErrorDoesNotSerializeValidMethod() throws Exception {
        JsonRpcError error = new JsonRpcError(-32600, "Invalid Request", null);
        JsonRpcResponse response = JsonRpcResponse.error(1, error);

        assertThatJson(mapper.writeValueAsString(response))
            .isEqualTo("""
                    {"jsonrpc":"2.0","id":1,"error":{"code":-32600,"message":"Invalid Request"}}""");

        JsonRpcResponse deserialized = mapper.readValue(mapper.writeValueAsString(response), JsonRpcResponse.class);
        assertEquals(response.jsonrpc(), deserialized.jsonrpc());
        assertEquals(response.error().code(), deserialized.error().code());
    }

    @Test
    void testContentDoesNotSerializeValidationMethods() throws Exception {
        Content textContent = Content.text("Hello, world!");

        assertThatJson(mapper.writeValueAsString(textContent))
            .isEqualTo("""
                    {"type":"text","text":"Hello, world!"}""");

        Content deserialized = mapper.readValue(mapper.writeValueAsString(textContent), Content.class);
        assertEquals(textContent, deserialized);
    }

    @Test
    void testContentImageDoesNotSerializeValidationMethods() throws Exception {
        Content imageContent = Content.image("base64data", "image/png");

        assertThatJson(mapper.writeValueAsString(imageContent))
            .isEqualTo("""
                    {"type":"image","data":"base64data","mimeType":"image/png"}""");

        Content deserialized = mapper.readValue(mapper.writeValueAsString(imageContent), Content.class);
        assertEquals(imageContent, deserialized);
    }
}
