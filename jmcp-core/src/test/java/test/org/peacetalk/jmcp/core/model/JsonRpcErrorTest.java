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
import tools.jackson.databind.ObjectMapper;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;

// NOTE: exact-key-name/field-count checks below pin the JSON-RPC 2.0 wire spec
// (a Java field rename could silently break it), NOT Jackson's ability to serialize.
class JsonRpcErrorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testErrorCreation() {
        JsonRpcError error = new JsonRpcError(-32600, "Invalid Request", null);

        assertEquals(-32600, error.code());
        assertEquals("Invalid Request", error.message());
        assertNull(error.data());
    }

    @Test
    void testErrorWithData() {
        Object data = "additional error data";
        JsonRpcError error = new JsonRpcError(-32603, "Internal error", data);

        assertEquals(-32603, error.code());
        assertEquals("Internal error", error.message());
        assertEquals(data, error.data());
    }

    @Test
    void testStandardErrorCodeConstants() {
        // Guards the exact numeric JSON-RPC 2.0 error codes against accidental edits.
        assertEquals(-32700, JsonRpcError.PARSE_ERROR);
        assertEquals(-32600, JsonRpcError.INVALID_REQUEST);
        assertEquals(-32601, JsonRpcError.METHOD_NOT_FOUND);
        assertEquals(-32602, JsonRpcError.INVALID_PARAMS);
        assertEquals(-32603, JsonRpcError.INTERNAL_ERROR);
    }

    @Test
    void testParseError() {
        JsonRpcError error = JsonRpcError.parseError("Parse failed");

        assertEquals(JsonRpcError.PARSE_ERROR, error.code());
        assertEquals("Parse failed", error.message());
        assertNull(error.data(), "factory-created error should have null data");
    }

    @Test
    void testMethodNotFound() {
        JsonRpcError error = JsonRpcError.methodNotFound("unknown_method");

        assertEquals(JsonRpcError.METHOD_NOT_FOUND, error.code());
        assertEquals("Method not found: unknown_method", error.message());
    }

    @Test
    void testInvalidParams() {
        JsonRpcError error = JsonRpcError.invalidParams("Missing parameter 'id'");

        assertEquals(JsonRpcError.INVALID_PARAMS, error.code());
        assertEquals("Missing parameter 'id'", error.message());
    }

    @Test
    void testInternalError() {
        JsonRpcError error = JsonRpcError.internalError("Database connection failed");

        assertEquals(JsonRpcError.INTERNAL_ERROR, error.code());
        assertEquals("Database connection failed", error.message());
    }

    @Test
    void testSerializesExactShapeAndOmitsNullData() {
        // code is a primitive int (always serialized); message present; null data omitted (NON_NULL).
        JsonRpcError error = new JsonRpcError(-32601, "Method not found: foo", null);

        assertThatJson(mapper.writeValueAsString(error))
            .isEqualTo("""
                    {"code":-32601,"message":"Method not found: foo"}""");
    }

    @Test
    void testCodeZeroIsSerialized() {
        // code is a primitive int, so even the value 0 must appear in the JSON
        // (a boxed Integer with NON_NULL would still serialize 0, but this pins
        //  that the field is never dropped for the default-looking value).
        JsonRpcError error = new JsonRpcError(0, "zero code", null);

        assertThatJson(mapper.writeValueAsString(error))
            .isEqualTo("""
                    {"code":0,"message":"zero code"}""");
    }

    @Test
    void testDataSerializedWhenPresent() {
        JsonRpcError error = new JsonRpcError(-32000, "custom", "extra-detail");

        assertThatJson(mapper.writeValueAsString(error))
            .isEqualTo("""
                    {"code":-32000,"message":"custom","data":"extra-detail"}""");
    }

    @Test
    void testRoundTrip() throws Exception {
        // data as a String survives; code and message survive.
        JsonRpcError original = new JsonRpcError(-32000, "custom", "extra-detail");

        String json = mapper.writeValueAsString(original);
        JsonRpcError deserialized = mapper.readValue(json, JsonRpcError.class);

        assertEquals(original.code(), deserialized.code());
        assertEquals(original.message(), deserialized.message());
        assertEquals(original.data(), deserialized.data(),
            "String data must round-trip back to an equal value");
    }

    @Test
    void testRoundTripWithNullData() throws Exception {
        JsonRpcError original = JsonRpcError.parseError("bad json");

        String json = mapper.writeValueAsString(original);
        JsonRpcError deserialized = mapper.readValue(json, JsonRpcError.class);

        assertEquals(original.code(), deserialized.code());
        assertEquals(original.message(), deserialized.message());
        assertNull(deserialized.data(), "absent data must deserialize back to null");
    }
}
