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
import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

// NOTE: exact-key-name/field-count checks below pin the JSON-RPC 2.0 wire spec
// (a Java field rename could silently break it), NOT Jackson's ability to serialize.
class JsonRpcResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testSuccessResponse() {
        Object result = "success result";
        JsonRpcResponse response = JsonRpcResponse.success(1, result);

        assertEquals("2.0", response.jsonrpc());
        assertEquals(1, response.id());
        assertEquals(result, response.result());
        assertNull(response.error());
    }

    @Test
    void testErrorResponse() {
        JsonRpcError error = new JsonRpcError(-32600, "Invalid Request", null);
        JsonRpcResponse response = JsonRpcResponse.error(2, error);

        assertEquals("2.0", response.jsonrpc());
        assertEquals(2, response.id());
        assertNull(response.result());
        assertEquals(error, response.error());
    }

    @Test
    void testResponseWithStringId() {
        JsonRpcResponse response = JsonRpcResponse.success("string-id", "result");
        assertEquals("string-id", response.id());
    }

    @Test
    void testDefaultJsonRpcVersion() {
        JsonRpcResponse response = new JsonRpcResponse(null, 1, "result", null);
        assertEquals("2.0", response.jsonrpc());
    }

    @Test
    void testMustHaveExactlyOneOfResultOrError() {
        JsonRpcError error = new JsonRpcError(-32600, "Invalid Request", null);

        assertThrows(IllegalArgumentException.class, () ->
            new JsonRpcResponse("2.0", 1, "result", error),
            "having both result and error must be rejected");
        assertThrows(IllegalArgumentException.class, () ->
            new JsonRpcResponse("2.0", 1, null, null),
            "having neither result nor error must be rejected");
    }

    @Test
    void testIsValidReflectsExclusiveInvariant() {
        // isValid() is the XOR of result/error presence; only constructible states
        // are exercised here since the constructor forbids the invalid ones.
        assertTrue(JsonRpcResponse.success(1, "r").isValid(), "success response must be valid");
        JsonRpcError error = new JsonRpcError(-32600, "Invalid Request", null);
        assertTrue(JsonRpcResponse.error(1, error).isValid(), "error response must be valid");
    }

    @Test
    void testSuccessResponseSerializesResultAndOmitsError() {
        // A success response must contain result and omit error (NON_NULL).
        JsonRpcResponse response = JsonRpcResponse.success(1, "the-result");

        JsonNode node = mapper.valueToTree(response);

        assertEquals("2.0", node.get("jsonrpc").asText());
        assertEquals(1, node.get("id").intValue());
        assertEquals("the-result", node.get("result").asText(), "result must serialize under key 'result'");
        assertFalse(node.has("error"), "null error must be omitted from a success response (NON_NULL)");
        assertEquals(3, node.size(), "success response should serialize exactly jsonrpc + id + result");
    }

    @Test
    void testErrorResponseSerializesErrorAndOmitsResult() {
        // An error response must contain error and omit result (NON_NULL).
        JsonRpcError error = new JsonRpcError(-32601, "Method not found: x", null);
        JsonRpcResponse response = JsonRpcResponse.error(9, error);

        JsonNode node = mapper.valueToTree(response);

        assertEquals("2.0", node.get("jsonrpc").asText());
        assertEquals(9, node.get("id").intValue());
        assertTrue(node.has("error"), "error response must serialize the 'error' field");
        assertFalse(node.has("result"), "null result must be omitted from an error response (NON_NULL)");
        assertEquals(-32601, node.get("error").get("code").intValue(),
            "nested error code must serialize under 'error.code'");
        assertEquals("Method not found: x", node.get("error").get("message").asText());
    }

    @Test
    void testSuccessResponseRoundTrip() throws Exception {
        JsonRpcResponse original = JsonRpcResponse.success(1, "the-result");

        String json = mapper.writeValueAsString(original);
        JsonRpcResponse deserialized = mapper.readValue(json, JsonRpcResponse.class);

        assertEquals(original.jsonrpc(), deserialized.jsonrpc());
        assertEquals(1, ((Number) deserialized.id()).intValue());
        assertEquals("the-result", deserialized.result());
        assertNull(deserialized.error(), "absent error must deserialize back to null");
    }

    @Test
    void testErrorResponseRoundTrip() throws Exception {
        JsonRpcError error = new JsonRpcError(-32602, "bad params", null);
        JsonRpcResponse original = JsonRpcResponse.error(5, error);

        String json = mapper.writeValueAsString(original);
        JsonRpcResponse deserialized = mapper.readValue(json, JsonRpcResponse.class);

        assertEquals(original.jsonrpc(), deserialized.jsonrpc());
        assertEquals(5, ((Number) deserialized.id()).intValue());
        assertNull(deserialized.result(), "absent result must deserialize back to null");
        assertNotNull(deserialized.error());
        assertEquals(-32602, deserialized.error().code(), "nested error code must round-trip");
        assertEquals("bad params", deserialized.error().message());
    }
}
