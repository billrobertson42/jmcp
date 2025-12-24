package test.org.peacetalk.jmcp.core.model;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.JsonRpcError;

import static org.junit.jupiter.api.Assertions.*;

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

