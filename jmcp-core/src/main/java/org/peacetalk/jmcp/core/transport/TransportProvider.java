package org.peacetalk.jmcp.core.transport;

/**
 * SPI interface for MCP transport providers.
 * Implementations are discovered via java.util.ServiceLoader.
 *
 * To register a transport provider, add to module-info.java:
 *   provides org.peacetalk.jmcp.core.transport.TransportProvider
 *       with com.example.MyTransportProvider;
 */
public interface TransportProvider {

    /** Human-readable name (e.g., "stdio", "sse", "websocket") */
    String getName();

    /** Create a new transport instance */
    McpTransport createTransport();

    /**
     * Priority for transport selection. Higher values = higher priority.
     * When multiple transports are available, the highest priority is used.
     * Default: 0
     */
    default int priority() {
        return 0;
    }
}

