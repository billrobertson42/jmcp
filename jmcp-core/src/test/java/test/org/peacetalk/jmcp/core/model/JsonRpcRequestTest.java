package test.org.peacetalk.jmcp.core.model;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;

import static org.junit.jupiter.api.Assertions.*;

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

