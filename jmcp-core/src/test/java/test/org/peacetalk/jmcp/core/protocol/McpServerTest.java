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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.McpProtocolHandler;
import org.peacetalk.jmcp.core.protocol.McpServer;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for McpServer's request dispatch. McpProtocolHandler is a small,
 * two-method interface, so a real implementation ({@link FakeProtocolHandler}
 * below) is exactly as easy to write as a mock configuration and lets these
 * tests track "was handle() called" with a plain counter instead of Mockito
 * verify/never().
 */
class McpServerTest {

    private McpServer server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        server = new McpServer();
        objectMapper = new ObjectMapper();
    }

    // ---- dispatch of valid requests -----------------------------------------

    @Test
    void testHandleValidRequestReturnsResultAndEchoesId() throws Exception {
        FakeProtocolHandler handler = FakeProtocolHandler.returning(
            Set.of("test_method"), JsonRpcResponse.success(1, "test result"));

        server.registerHandler(handler);

        String responseJson = server.handleRequest("""
                {"jsonrpc":"2.0","id":1,"method":"test_method"}""");

        assertNotNull(responseJson);
        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertEquals("2.0", response.jsonrpc());
        assertEquals(1, response.id());
        assertEquals("test result", response.result());
        assertNull(response.error());

        assertEquals(1, handler.handleCalls, "handle() should be invoked exactly once");
    }

    @Test
    void testRequestPassesParsedMethodAndParamsToHandler() throws Exception {
        // Guards the parse+dispatch path: the handler must receive the deserialized
        // request with the correct method (not a mangled/empty request).
        FakeProtocolHandler handler = new FakeProtocolHandler(Set.of("echo"),
            req -> JsonRpcResponse.success(req.id(), req.method()));

        server.registerHandler(handler);

        String responseJson = server.handleRequest("""
                {"jsonrpc":"2.0","id":99,"method":"echo","params":{"a":1}}""");

        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertEquals(99, response.id());
        assertEquals("echo", response.result(),
            "handler should have received the parsed method name");
    }

    // ---- method-not-found ----------------------------------------------------

    @Test
    void testHandleMethodNotFound() throws Exception {
        FakeProtocolHandler handler = FakeProtocolHandler.returning(
            Set.of("known_method"), JsonRpcResponse.success(1, "unused"));
        server.registerHandler(handler);

        String responseJson = server.handleRequest("""
                {"jsonrpc":"2.0","id":1,"method":"unknown_method"}""");

        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertNull(response.result());
        assertNotNull(response.error());
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.error().code());
        assertTrue(response.error().message().contains("unknown_method"),
            "error should name the missing method: " + response.error().message());
        assertEquals(1, response.id(), "error response must echo request id");
        // The registered handler must not be invoked for a method it does not own.
        assertEquals(0, handler.handleCalls, "handle() must not be called for an unowned method");
    }

    @Test
    void testMethodNotFoundWithNoHandlersRegistered() throws Exception {
        String responseJson = server.handleRequest("""
                {"jsonrpc":"2.0","id":5,"method":"anything"}""");

        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertNotNull(response.error());
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.error().code(),
            "empty dispatch table must still yield METHOD_NOT_FOUND, not a crash");
        assertEquals(5, response.id());
    }

    // ---- parse errors --------------------------------------------------------

    @Test
    void testHandleInvalidJsonReturnsParseErrorWithNullId() throws Exception {
        String responseJson = server.handleRequest("not valid json");

        assertNotNull(responseJson);
        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertNotNull(response.error());
        assertEquals(JsonRpcError.PARSE_ERROR, response.error().code(),
            "unparseable input must map to JSON-RPC PARSE_ERROR (-32700)");
        // Per JSON-RPC, a parse error cannot know the request id, so it must be null.
        assertNull(response.id(), "parse-error response id must be null");
    }

    // ---- handler exceptions --------------------------------------------------

    @Test
    void testHandlerThrowsMapsToInternalError() throws Exception {
        FakeProtocolHandler handler = FakeProtocolHandler.throwing(
            Set.of("error_method"), new RuntimeException("Handler error"));

        server.registerHandler(handler);

        String responseJson = server.handleRequest("""
                {"jsonrpc":"2.0","id":1,"method":"error_method"}""");

        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertNull(response.result());
        assertNotNull(response.error());
        assertEquals(JsonRpcError.INTERNAL_ERROR, response.error().code(),
            "a thrown handler exception must be wrapped as INTERNAL_ERROR (-32603)");
        assertTrue(response.error().message().contains("Handler error"),
            "error message should surface the underlying cause: " + response.error().message());
        assertEquals(1, response.id(), "error response must still echo request id");
    }

    // ---- notifications (null id) --------------------------------------------

    @Test
    void testNotificationForKnownMethodProducesNoResponseButIsDispatched() throws Exception {
        // A request with null id is a notification: the handler is invoked but no
        // response string is returned to the caller.
        FakeProtocolHandler handler = new FakeProtocolHandler(
            Set.of("notifications/something"), req -> null);

        server.registerHandler(handler);

        String responseJson = server.handleRequest("""
                {"jsonrpc":"2.0","method":"notifications/something"}""");

        assertNull(responseJson, "notifications must not produce a response");
        assertEquals(1, handler.handleCalls, "handle() should still be invoked for a notification");
    }

    @Test
    void testUnknownNotificationIsSilentlyDropped() throws Exception {
        // Unknown notifications must be ignored (no response, no crash), never routed
        // to a handler that does not own the method.
        FakeProtocolHandler handler = FakeProtocolHandler.returning(
            Set.of("known_method"), JsonRpcResponse.success(1, "unused"));
        server.registerHandler(handler);

        String responseJson = server.handleRequest("""
                {"jsonrpc":"2.0","method":"notifications/unknown"}""");

        assertNull(responseJson, "unknown notification must produce no response");
        assertEquals(0, handler.handleCalls, "handle() must not be called for an unowned notification");
    }

    @Test
    void testNotificationHandlerExceptionIsSwallowed() throws Exception {
        // An exception while handling a notification must not propagate and must not
        // produce a response (there is no id to respond to).
        FakeProtocolHandler handler = FakeProtocolHandler.throwing(
            Set.of("notifications/boom"), new RuntimeException("boom"));

        server.registerHandler(handler);

        String responseJson = assertDoesNotThrow(() ->
            server.handleRequest("""
                    {"jsonrpc":"2.0","method":"notifications/boom"}"""));

        assertNull(responseJson, "a failing notification must still produce no response");
    }

    // ---- multiple handlers ---------------------------------------------------

    @Test
    void testMultipleHandlersRouteToCorrectOwner() throws Exception {
        FakeProtocolHandler handler1 = FakeProtocolHandler.returning(
            Set.of("method1"), JsonRpcResponse.success(1, "result1"));
        FakeProtocolHandler handler2 = FakeProtocolHandler.returning(
            Set.of("method2"), JsonRpcResponse.success(2, "result2"));

        server.registerHandler(handler1);
        server.registerHandler(handler2);

        JsonRpcResponse response1 = objectMapper.readValue(
            server.handleRequest("""
                    {"jsonrpc":"2.0","id":1,"method":"method1"}"""),
            JsonRpcResponse.class);
        assertEquals("result1", response1.result());
        assertEquals(1, handler1.handleCalls);
        assertEquals(0, handler2.handleCalls, "handler2 must not see a request routed to handler1");

        JsonRpcResponse response2 = objectMapper.readValue(
            server.handleRequest("""
                    {"jsonrpc":"2.0","id":2,"method":"method2"}"""),
            JsonRpcResponse.class);
        assertEquals("result2", response2.result());
        assertEquals(1, handler2.handleCalls);
    }

    // ---- registration --------------------------------------------------------

    @Test
    void testDuplicateMethodRegistrationThrowsAndNamesConflict() {
        FakeProtocolHandler handler1 = FakeProtocolHandler.returning(
            Set.of("duplicate_method"), JsonRpcResponse.success(1, "unused"));
        FakeProtocolHandler handler2 = FakeProtocolHandler.returning(
            Set.of("duplicate_method"), JsonRpcResponse.success(2, "unused"));

        server.registerHandler(handler1);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> server.registerHandler(handler2));

        assertTrue(exception.getMessage().contains("duplicate_method"),
            "exception should name the conflicting method: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("already registered"),
            "exception should explain the conflict: " + exception.getMessage());
    }

    @Test
    void testFirstHandlerStillServesAfterDuplicateRegistrationFails() throws Exception {
        // A rejected duplicate registration must not corrupt the dispatch table:
        // the originally-registered handler must keep serving the method.
        FakeProtocolHandler handler1 = FakeProtocolHandler.returning(
            Set.of("shared"), JsonRpcResponse.success(1, "from-handler1"));
        FakeProtocolHandler handler2 = FakeProtocolHandler.returning(
            Set.of("shared"), JsonRpcResponse.success(2, "from-handler2"));

        server.registerHandler(handler1);
        assertThrows(IllegalStateException.class, () -> server.registerHandler(handler2));

        JsonRpcResponse response = objectMapper.readValue(
            server.handleRequest("""
                    {"jsonrpc":"2.0","id":1,"method":"shared"}"""),
            JsonRpcResponse.class);
        assertEquals("from-handler1", response.result(),
            "the first-registered handler must still own the method");
        assertEquals(0, handler2.handleCalls, "the rejected duplicate handler must never be invoked");
    }

    // ---- test doubles --------------------------------------------------------
    // McpProtocolHandler is a small, two-method interface, so a real
    // implementation is exactly as easy to write as a mock configuration.

    private static final class FakeProtocolHandler implements McpProtocolHandler {
        private final Set<String> supportedMethods;
        private final Function<JsonRpcRequest, JsonRpcResponse> responder;
        int handleCalls;

        FakeProtocolHandler(Set<String> supportedMethods, Function<JsonRpcRequest, JsonRpcResponse> responder) {
            this.supportedMethods = supportedMethods;
            this.responder = responder;
        }

        /** A handler that always returns the same fixed response. */
        static FakeProtocolHandler returning(Set<String> supportedMethods, JsonRpcResponse response) {
            return new FakeProtocolHandler(supportedMethods, req -> response);
        }

        /** A handler whose handle() always throws, to exercise error-mapping paths. */
        static FakeProtocolHandler throwing(Set<String> supportedMethods, RuntimeException failure) {
            return new FakeProtocolHandler(supportedMethods, req -> { throw failure; });
        }

        @Override
        public JsonRpcResponse handle(JsonRpcRequest request) {
            handleCalls++;
            return responder.apply(request);
        }

        @Override
        public Set<String> getSupportedMethods() {
            return supportedMethods;
        }
    }
}
