package org.peacetalk.jmcp.core.transport;

/**
 * Abstraction for MCP transport mechanisms (stdio, SSE, etc.)
 */
public interface McpTransport {
    /**
     * Start the transport and begin handling requests
     */
    void start(McpRequestHandler handler) throws Exception;

    /**
     * Stop the transport and cleanup resources
     */
    void stop() throws Exception;

    /**
     * Check if transport is running
     */
    boolean isRunning();
}

