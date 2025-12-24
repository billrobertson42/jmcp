package test.org.peacetalk.jmcp.core.model;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.JsonRpcError;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;

import static org.junit.jupiter.api.Assertions.*;

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

