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

package test.org.peacetalk.jmcp.core.protocol;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.InitializeResult;
import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.InitializationHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InitializationHandlerTest {

    private final InitializationHandler handler = new InitializationHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    // ---- getSupportedMethods -------------------------------------------------

    @Test
    void testSupportedMethodsAreExactlyLifecycleMethods() {
        Set<String> supported = handler.getSupportedMethods();
        // Exact contents: catches accidental additions/removals to the dispatch set.
        assertEquals(Set.of("initialize", "notifications/initialized", "ping"), supported,
            "InitializationHandler must advertise exactly the three lifecycle methods");
    }

    @Test
    void testDoesNotClaimToolsOrResourceMethods() {
        Set<String> supported = handler.getSupportedMethods();
        assertFalse(supported.contains("tools/list"),
            "Initialization handler must not claim tools/list (owned by ToolsHandler)");
        assertFalse(supported.contains("resources/list"),
            "Initialization handler must not claim resources/list (owned by ResourcesHandler)");
    }

    // ---- initialize ----------------------------------------------------------

    @Test
    void testInitializeReturnsProtocolVersionServerInfoAndCapabilities() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "initialize", null);
        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertEquals("2.0", response.jsonrpc());
        assertEquals(1, response.id(), "response id must echo the request id");
        assertNull(response.error(), "initialize must not produce a JSON-RPC error");
        assertNotNull(response.result());

        InitializeResult result = mapper.convertValue(response.result(), InitializeResult.class);
        assertEquals("2024-11-05", result.protocolVersion(), "protocol version must match MCP spec revision");
        assertNotNull(result.serverInfo());
        assertEquals("jmcp", result.serverInfo().name());
        assertEquals("1.0.0", result.serverInfo().version());
    }

    @Test
    void testInitializeDefaultConstructorAdvertisesBothToolsAndResources() {
        // Default constructor => hasTools=true, hasResources=true.
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "initialize", null);
        JsonRpcResponse response = handler.handle(request);

        InitializeResult result = mapper.convertValue(response.result(), InitializeResult.class);
        assertNotNull(result.capabilities().tools(), "default handler must advertise tools capability");
        assertNotNull(result.capabilities().resources(), "default handler must advertise resources capability");
        assertFalse(result.capabilities().tools().listChanged(),
            "tools.listChanged is hard-coded false (server does not emit list-changed notifications)");
        assertFalse(result.capabilities().resources().subscribe(),
            "resources.subscribe is hard-coded false");
        assertFalse(result.capabilities().resources().listChanged(),
            "resources.listChanged is hard-coded false");
    }

    @Test
    void testInitializeWithNoToolsOmitsToolsCapability() {
        // hasTools=false must produce a null tools capability so the client does not
        // believe the server exposes tools when none were assembled.
        InitializationHandler noTools = new InitializationHandler(false, true);
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "initialize", null);

        JsonRpcResponse response = noTools.handle(request);
        InitializeResult result = mapper.convertValue(response.result(), InitializeResult.class);

        assertNull(result.capabilities().tools(),
            "tools capability must be null when no tool providers were registered");
        assertNotNull(result.capabilities().resources(),
            "resources capability must still be advertised");
    }

    @Test
    void testInitializeWithNoResourcesOmitsResourcesCapability() {
        InitializationHandler noResources = new InitializationHandler(true, false);
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "initialize", null);

        JsonRpcResponse response = noResources.handle(request);
        InitializeResult result = mapper.convertValue(response.result(), InitializeResult.class);

        assertNull(result.capabilities().resources(),
            "resources capability must be null when no resource providers were registered");
        assertNotNull(result.capabilities().tools(),
            "tools capability must still be advertised");
    }

    @Test
    void testInitializeWithNoCapabilitiesOmitsBoth() {
        InitializationHandler bare = new InitializationHandler(false, false);
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "initialize", null);

        JsonRpcResponse response = bare.handle(request);
        InitializeResult result = mapper.convertValue(response.result(), InitializeResult.class);

        assertNull(result.capabilities().tools());
        assertNull(result.capabilities().resources());
    }

    // ---- ping ----------------------------------------------------------------

    @Test
    void testPingReturnsEmptyObjectResult() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 42, "ping", null);
        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error(), "ping must not produce an error");
        assertEquals(42, response.id(), "ping response id must echo request id");
        assertNotNull(response.result());

        JsonNode result = mapper.valueToTree(response.result());
        assertTrue(result.isObject(), "ping result must be a JSON object per MCP spec");
        assertEquals(0, result.size(), "ping result must be an empty object");
    }

    // ---- notifications/initialized ------------------------------------------

    @Test
    void testInitializedNotificationReturnsNullResponse() {
        // Notifications receive no response; the handler signals this by returning null.
        JsonRpcRequest request = new JsonRpcRequest("2.0", null, "notifications/initialized", null);
        JsonRpcResponse response = handler.handle(request);

        assertNull(response, "notifications/initialized must return null (no response for notifications)");
    }

    @Test
    void testIsInitializedFlipsAfterInitializedNotification() {
        assertFalse(handler.isInitialized(), "handler must start uninitialized");

        handler.handle(new JsonRpcRequest("2.0", null, "notifications/initialized", null));

        assertTrue(handler.isInitialized(),
            "isInitialized must become true after processing the initialized notification");
    }

    // ---- unknown method dispatch --------------------------------------------

    @Test
    void testUnknownMethodReturnsMethodNotFound() {
        // Even though McpServer routes by method, handle() is also reachable directly and
        // its switch default must produce a proper -32601 error rather than throwing/NPE.
        JsonRpcRequest request = new JsonRpcRequest("2.0", 7, "no/such/method", null);
        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.result());
        assertNotNull(response.error());
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.error().code(),
            "unknown method must map to JSON-RPC METHOD_NOT_FOUND (-32601)");
        assertTrue(response.error().message().contains("no/such/method"),
            "error message should name the offending method: " + response.error().message());
        assertEquals(7, response.id(), "error response id must echo request id");
    }
}
