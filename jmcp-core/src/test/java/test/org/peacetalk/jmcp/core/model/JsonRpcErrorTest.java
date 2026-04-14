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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonRpcErrorTest {

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
    void testParseError() {
        JsonRpcError error = JsonRpcError.parseError("Parse failed");

        assertEquals(JsonRpcError.PARSE_ERROR, error.code());
        assertEquals("Parse failed", error.message());
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
}

