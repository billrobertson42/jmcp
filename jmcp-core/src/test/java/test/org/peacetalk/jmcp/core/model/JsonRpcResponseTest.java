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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonRpcResponseTest {

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
}

