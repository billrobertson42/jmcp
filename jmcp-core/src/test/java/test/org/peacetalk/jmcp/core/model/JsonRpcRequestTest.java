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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonRpcRequestTest {

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
}

