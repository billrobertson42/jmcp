package org.peacetalk.jmcp.client.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CommunicationLogger.
 */
class CommunicationLoggerTest {
    private CommunicationLogger logger;

    @BeforeEach
    void setUp() {
        logger = new CommunicationLogger();
    }

    @Test
    void testInitialLogIsEmpty() {
        String log = logger.getFormattedLog();
        assertEquals("", log);
    }

    @Test
    void testLogRequest() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "testMethod", null);

        logger.logRequest(request);
        String log = logger.getFormattedLog();

        assertNotNull(log);
        assertTrue(log.contains("SENT"));
        assertTrue(log.contains("testMethod"));
        assertTrue(log.contains("===="));
    }

    @Test
    void testLogResponse() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", 1L, "test result", null);

        logger.logResponse(response);
        String log = logger.getFormattedLog();

        assertNotNull(log);
        assertTrue(log.contains("RECEIVED"));
        assertTrue(log.contains("Response"));
        assertTrue(log.contains("===="));
    }

    @Test
    void testLogError() {
        Exception exception = new RuntimeException("Test error");

        logger.logError("Connection failed", exception);
        String log = logger.getFormattedLog();

        assertNotNull(log);
        assertTrue(log.contains("ERROR"));
        assertTrue(log.contains("Connection failed"));
        assertTrue(log.contains("RuntimeException"));
        assertTrue(log.contains("Test error"));
        assertTrue(log.contains("!!!!"));
    }

    @Test
    void testLogErrorWithNullException() {
        logger.logError("Error without exception", null);
        String log = logger.getFormattedLog();

        assertNotNull(log);
        assertTrue(log.contains("ERROR"));
        assertTrue(log.contains("Error without exception"));
        assertFalse(log.contains("Exception:"));
    }

    @Test
    void testMultipleLogs() {
        JsonRpcRequest request1 = new JsonRpcRequest("2.0", 1L, "method1", null);
        JsonRpcRequest request2 = new JsonRpcRequest("2.0", 2L, "method2", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", 1L, "result", null);

        logger.logRequest(request1);
        logger.logRequest(request2);
        logger.logResponse(response);

        String log = logger.getFormattedLog();

        assertTrue(log.contains("method1"));
        assertTrue(log.contains("method2"));
        assertTrue(log.contains("RECEIVED"));

        // Check that entries appear in order
        int method1Pos = log.indexOf("method1");
        int method2Pos = log.indexOf("method2");
        int responsePos = log.indexOf("RECEIVED");

        assertTrue(method1Pos < method2Pos);
        assertTrue(method2Pos < responsePos);
    }

    @Test
    void testClear() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "testMethod", null);

        logger.logRequest(request);
        assertFalse(logger.getFormattedLog().isEmpty());

        logger.clear();
        assertEquals("", logger.getFormattedLog());
    }

    @Test
    void testClearMultipleTimes() {
        logger.logRequest(new JsonRpcRequest("2.0", 1L, "test", null));
        logger.clear();
        logger.clear(); // Clear again on empty log

        assertEquals("", logger.getFormattedLog());
    }

    @Test
    void testLogAfterClear() {
        JsonRpcRequest request1 = new JsonRpcRequest("2.0", 1L, "method1", null);
        JsonRpcRequest request2 = new JsonRpcRequest("2.0", 2L, "method2", null);

        logger.logRequest(request1);
        logger.clear();
        logger.logRequest(request2);

        String log = logger.getFormattedLog();
        assertFalse(log.contains("method1"));
        assertTrue(log.contains("method2"));
    }

    @Test
    void testSeparatorLines() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "test", null);

        logger.logRequest(request);
        String log = logger.getFormattedLog();

        // Check for separator lines (80 equals signs)
        assertTrue(log.contains("=".repeat(80)));
    }

    @Test
    void testErrorSeparatorLines() {
        logger.logError("Test error", null);
        String log = logger.getFormattedLog();

        // Check for error separator lines (80 exclamation marks)
        assertTrue(log.contains("!".repeat(80)));
    }

    @Test
    void testJsonFormatting() {
        // Create request with parameters
        String params = "{\"key\":\"value\"}";
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "test", params);

        logger.logRequest(request);
        String log = logger.getFormattedLog();

        // Should contain formatted JSON (with newlines/indentation from pretty printer)
        assertTrue(log.contains("jsonrpc"));
        assertTrue(log.contains("test"));
    }

    @Test
    void testLogPreservesContent() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "test", "param");

        logger.logRequest(request);
        String log1 = logger.getFormattedLog();

        // Getting the log multiple times should return same content
        String log2 = logger.getFormattedLog();

        assertEquals(log1, log2);
    }

    @Test
    void testLogResponseWithError() {
        // Response with error instead of result
        JsonRpcResponse response = new JsonRpcResponse("2.0", 1L, null,
            new org.peacetalk.jmcp.core.model.JsonRpcError(-32600, "Invalid Request", null));

        logger.logResponse(response);
        String log = logger.getFormattedLog();

        assertTrue(log.contains("RECEIVED"));
        // Error details should be in the JSON
        assertTrue(log.contains("error") || log.contains("Invalid Request"));
    }
}

