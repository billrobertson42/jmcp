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
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import tools.jackson.databind.ObjectMapper;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

// NOTE: exact-key-name/field-count checks below pin the JSON-RPC 2.0 wire spec
// (a Java field rename could silently break it), NOT Jackson's ability to serialize.
class JsonRpcRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testRequestCreation() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "test_method", null);

        assertEquals("2.0", request.jsonrpc());
        assertEquals(1, request.id());
        assertEquals("test_method", request.method());
        assertNull(request.params());
    }

    @Test
    void testRequestWithParams() {
        Object params = "test params";
        JsonRpcRequest request = new JsonRpcRequest("2.0", "test-id", "method", params);

        assertEquals("2.0", request.jsonrpc());
        assertEquals("test-id", request.id());
        assertEquals("method", request.method());
        assertEquals(params, request.params());
    }

    @Test
    void testDefaultJsonRpcVersion() {
        JsonRpcRequest request = new JsonRpcRequest(null, 1, "test", null);
        assertEquals("2.0", request.jsonrpc());
    }

    @Test
    void testBlankMethodRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new JsonRpcRequest("2.0", 1, "", null),
            "empty method must be rejected by the compact constructor");
        assertThrows(IllegalArgumentException.class, () ->
            new JsonRpcRequest("2.0", 1, "   ", null),
            "blank method must be rejected by the compact constructor");
        assertThrows(IllegalArgumentException.class, () ->
            new JsonRpcRequest("2.0", 1, null, null),
            "null method must be rejected by the compact constructor");
    }

    @Test
    void testSerializesExactShapeAndOmitsNullFields() {
        // A request with no id and no params must omit both fields (NON_NULL),
        // and jsonrpc/method must use their exact JSON keys.
        JsonRpcRequest request = new JsonRpcRequest("2.0", null, "tools/list", null);

        assertThatJson(mapper.writeValueAsString(request))
            .isEqualTo("""
                    {"jsonrpc":"2.0","method":"tools/list"}""");
    }

    @Test
    void testSerializesIdAndParamsWhenPresent() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 7, "ping", "some-params");

        assertThatJson(mapper.writeValueAsString(request))
            .isEqualTo("""
                    {"jsonrpc":"2.0","id":7,"method":"ping","params":"some-params"}""");
    }

    @Test
    void testRoundTripWithNumericId() throws Exception {
        JsonRpcRequest original = new JsonRpcRequest("2.0", 42, "tools/call", null);

        String json = mapper.writeValueAsString(original);
        JsonRpcRequest deserialized = mapper.readValue(json, JsonRpcRequest.class);

        assertEquals(original.jsonrpc(), deserialized.jsonrpc());
        assertEquals(original.method(), deserialized.method());
        assertEquals(42, ((Number) deserialized.id()).intValue(), "numeric id must round-trip");
        assertNull(deserialized.params(), "absent params must deserialize back to null");
    }

    @Test
    void testRoundTripWithStringId() throws Exception {
        JsonRpcRequest original = new JsonRpcRequest("2.0", "abc-123", "tools/call", null);

        String json = mapper.writeValueAsString(original);
        JsonRpcRequest deserialized = mapper.readValue(json, JsonRpcRequest.class);

        assertEquals("abc-123", deserialized.id(), "string id must round-trip unchanged");
    }

    @Test
    void testDeserializeAppliesDefaultVersionWhenAbsent() throws Exception {
        // jsonrpc absent from the wire => compact constructor defaults it to "2.0".
        String json = """
                {"id":1,"method":"ping"}""";

        JsonRpcRequest request = mapper.readValue(json, JsonRpcRequest.class);

        assertEquals("2.0", request.jsonrpc(), "missing jsonrpc must default to 2.0 on deserialize");
        assertEquals("ping", request.method());
    }
}
