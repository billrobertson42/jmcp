package test.org.peacetalk.jmcp.core.protocol;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.InitializeResult;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.InitializationHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InitializationHandlerTest {

    private final InitializationHandler handler = new InitializationHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testSupportsInitialize() {
        Set<String> supported = handler.getSupportedMethods();
        assertTrue(supported.contains("initialize"));
    }

    @Test
    void testDoesNotSupportOtherMethods() {
        Set<String> supported = handler.getSupportedMethods();
        assertFalse(supported.contains("tools/list"));
        assertFalse(supported.contains("tools/call"));
        assertFalse(supported.contains("unknown"));
        assertEquals(3, supported.size()); // "initialize", "notifications/initialized", and "ping"
        assertTrue(supported.contains("initialize"));
        assertTrue(supported.contains("notifications/initialized"));
        assertTrue(supported.contains("ping"));
    }

    @Test
    void testInitializeResponse() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "initialize", null);
        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertEquals("2.0", response.jsonrpc());
        assertEquals(1, response.id());
        assertNull(response.error());
        assertNotNull(response.result());

        // Verify the result is a proper InitializeResult
        InitializeResult result = mapper.convertValue(response.result(), InitializeResult.class);
        assertNotNull(result);
        assertEquals("2024-11-05", result.protocolVersion());
        assertNotNull(result.serverInfo());
        assertEquals("jmcp", result.serverInfo().name());
        assertEquals("1.0.0", result.serverInfo().version());
        assertNotNull(result.capabilities());
        assertNotNull(result.capabilities().tools());
        assertFalse(result.capabilities().tools().listChanged());
    }
}

