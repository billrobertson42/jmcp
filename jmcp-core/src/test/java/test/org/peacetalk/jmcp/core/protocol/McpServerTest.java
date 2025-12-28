package test.org.peacetalk.jmcp.core.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.McpProtocolHandler;
import org.peacetalk.jmcp.core.protocol.McpServer;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpServerTest {

    private McpServer server;
    private ObjectMapper objectMapper;

    @Mock
    private McpProtocolHandler mockHandler;

    @BeforeEach
    void setUp() {
        server = new McpServer();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testHandleValidRequest() throws Exception {
        // Setup
        when(mockHandler.canHandle("test_method")).thenReturn(true);
        when(mockHandler.handle(any(JsonRpcRequest.class)))
            .thenReturn(JsonRpcResponse.success(1, "test result"));

        server.registerHandler(mockHandler);

        String requestJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test_method\"}";

        // Execute
        String responseJson = server.handleRequest(requestJson);

        // Verify
        assertNotNull(responseJson);
        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertEquals("2.0", response.jsonrpc());
        assertEquals(1, response.id());
        assertEquals("test result", response.result());

        verify(mockHandler).canHandle("test_method");
        verify(mockHandler).handle(any(JsonRpcRequest.class));
    }

    @Test
    void testHandleMethodNotFound() throws Exception {
        // Setup
        when(mockHandler.canHandle(anyString())).thenReturn(false);
        server.registerHandler(mockHandler);

        String requestJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown_method\"}";

        // Execute
        String responseJson = server.handleRequest(requestJson);

        // Verify
        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertNotNull(response.error());
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.error().code());
        assertTrue(response.error().message().contains("unknown_method"));
    }

    @Test
    void testHandleInvalidJson() {
        // Execute
        String responseJson = server.handleRequest("not valid json");

        // Verify
        assertNotNull(responseJson);
        assertTrue(responseJson.contains("Parse") || responseJson.contains("parse"),
            "Response should contain parse error: " + responseJson);
    }

    @Test
    void testHandlerThrowsException() throws Exception {
        // Setup
        when(mockHandler.canHandle("error_method")).thenReturn(true);
        when(mockHandler.handle(any(JsonRpcRequest.class)))
            .thenThrow(new RuntimeException("Handler error"));

        server.registerHandler(mockHandler);

        String requestJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"error_method\"}";

        // Execute
        String responseJson = server.handleRequest(requestJson);

        // Verify
        JsonRpcResponse response = objectMapper.readValue(responseJson, JsonRpcResponse.class);
        assertNotNull(response.error());
        assertEquals(JsonRpcError.INTERNAL_ERROR, response.error().code());
        assertTrue(response.error().message().contains("Handler error"));
    }

    @Test
    void testMultipleHandlers() throws Exception {
        // Setup
        McpProtocolHandler handler1 = mock(McpProtocolHandler.class);
        McpProtocolHandler handler2 = mock(McpProtocolHandler.class);

        when(handler1.canHandle("method1")).thenReturn(true);
        when(handler1.canHandle("method2")).thenReturn(false);
        when(handler1.handle(any())).thenReturn(JsonRpcResponse.success(1, "result1"));

        when(handler2.canHandle("method1")).thenReturn(false);
        when(handler2.canHandle("method2")).thenReturn(true);
        when(handler2.handle(any())).thenReturn(JsonRpcResponse.success(2, "result2"));

        server.registerHandler(handler1);
        server.registerHandler(handler2);

        // Execute method1
        String requestJson1 = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"method1\"}";
        String responseJson1 = server.handleRequest(requestJson1);
        JsonRpcResponse response1 = objectMapper.readValue(responseJson1, JsonRpcResponse.class);
        assertEquals("result1", response1.result());
        verify(handler1).handle(any());
        verify(handler2, never()).handle(any());

        // Execute method2
        String requestJson2 = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"method2\"}";
        String responseJson2 = server.handleRequest(requestJson2);
        JsonRpcResponse response2 = objectMapper.readValue(responseJson2, JsonRpcResponse.class);
        assertEquals("result2", response2.result());
    }
}

