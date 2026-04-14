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

package test.peacetalk.jmcp.client.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.client.service.CommunicationLogger;
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
    void testFormatRequest() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "testMethod", null);

        String logEntry = logger.formatRequest(request);

        assertNotNull(logEntry);
        assertTrue(logEntry.contains("SENT"));
        assertTrue(logEntry.contains("testMethod"));
        assertTrue(logEntry.contains("===="));
    }

    @Test
    void testFormatResponse() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", 1L, "test result", null);

        String logEntry = logger.formatResponse(response);

        assertNotNull(logEntry);
        assertTrue(logEntry.contains("RECEIVED"));
        assertTrue(logEntry.contains("Response"));
        assertTrue(logEntry.contains("===="));
    }

    @Test
    void testFormatError() {
        Exception exception = new RuntimeException("Test error");

        String logEntry = logger.formatError("Connection failed", exception);

        assertNotNull(logEntry);
        assertTrue(logEntry.contains("ERROR"));
        assertTrue(logEntry.contains("Connection failed"));
        assertTrue(logEntry.contains("RuntimeException"));
        assertTrue(logEntry.contains("Test error"));
        assertTrue(logEntry.contains("!!!!"));
    }

    @Test
    void testFormatErrorWithNullException() {
        String logEntry = logger.formatError("Error without exception", null);

        assertNotNull(logEntry);
        assertTrue(logEntry.contains("ERROR"));
        assertTrue(logEntry.contains("Error without exception"));
        assertFalse(logEntry.contains("Exception:"));
    }

    @Test
    void testMultipleFormats() {
        JsonRpcRequest request1 = new JsonRpcRequest("2.0", 1L, "method1", null);
        JsonRpcRequest request2 = new JsonRpcRequest("2.0", 2L, "method2", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", 1L, "result", null);

        String log1 = logger.formatRequest(request1);
        String log2 = logger.formatRequest(request2);
        String log3 = logger.formatResponse(response);

        assertTrue(log1.contains("method1"));
        assertTrue(log2.contains("method2"));
        assertTrue(log3.contains("RECEIVED"));

        // Each should be independent
        assertFalse(log1.contains("method2"));
        assertFalse(log2.contains("method1"));
    }

    @Test
    void testSeparatorLines() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "test", null);

        String logEntry = logger.formatRequest(request);

        // Check for separator lines (80 equals signs)
        assertTrue(logEntry.contains("=".repeat(80)));
    }

    @Test
    void testErrorSeparatorLines() {
        String logEntry = logger.formatError("Test error", null);

        // Check for error separator lines (80 exclamation marks)
        assertTrue(logEntry.contains("!".repeat(80)));
    }

    @Test
    void testJsonFormatting() {
        // Create request with parameters
        String params = "{\"key\":\"value\"}";
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "test", params);

        String logEntry = logger.formatRequest(request);

        // Should contain formatted JSON (with newlines/indentation from pretty printer)
        assertTrue(logEntry.contains("jsonrpc"));
        assertTrue(logEntry.contains("test"));
    }

    @Test
    void testResponseWithError() {
        // Response with error instead of result
        JsonRpcResponse response = new JsonRpcResponse("2.0", 1L, null,
            new org.peacetalk.jmcp.core.model.JsonRpcError(-32600, "Invalid Request", null));

        String logEntry = logger.formatResponse(response);

        assertTrue(logEntry.contains("RECEIVED"));
        // Error details should be in the JSON
        assertTrue(logEntry.contains("error") || logEntry.contains("Invalid Request"));
    }

    @Test
    void testFormatReturnsSeparateStrings() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1L, "test", null);

        String entry1 = logger.formatRequest(request);
        String entry2 = logger.formatRequest(request);

        // Should return same formatted string each time (stateless)
        assertEquals(entry1, entry2);
    }
}


