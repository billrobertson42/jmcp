package test.org.peacetalk.jmcp.transport.stdio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.peacetalk.jmcp.core.transport.McpRequestHandler;
import org.peacetalk.jmcp.transport.stdio.StdioTransport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StdioTransportTest {

    @Mock
    private McpRequestHandler mockHandler;

    @Test
    void testInitiallyNotRunning() {
        StdioTransport transport = new StdioTransport();
        assertFalse(transport.isRunning());
    }

    @Test
    void testCannotStartTwice() throws Exception {
        StdioTransport transport = new StdioTransport();

        // Start in a separate thread since it blocks
        Thread startThread = new Thread(() -> {
            try {
                transport.start(mockHandler);
            } catch (Exception e) {
                // Expected to be interrupted
            }
        });
        startThread.start();

        // Wait a bit for the transport to start
        Thread.sleep(100);

        assertTrue(transport.isRunning());

        // Try to start again
        assertThrows(IllegalStateException.class, () -> transport.start(mockHandler));

        // Cleanup
        transport.stop();
        startThread.join(1000);
    }

    @Test
    void testStopWhenNotRunning() throws Exception {
        StdioTransport transport = new StdioTransport();

        // Should not throw
        assertDoesNotThrow(() -> transport.stop());
    }

    @Test
    void testStartAndStop() throws Exception {
        StdioTransport transport = new StdioTransport();
        when(mockHandler.handleRequest(anyString())).thenReturn("{\"result\":\"ok\"}");

        // Start in a separate thread
        Thread startThread = new Thread(() -> {
            try {
                transport.start(mockHandler);
            } catch (Exception e) {
                // Expected
            }
        });
        startThread.start();

        // Wait for start
        Thread.sleep(100);
        assertTrue(transport.isRunning());

        // Stop
        transport.stop();
        startThread.join(1000);

        assertFalse(transport.isRunning());
    }
}

