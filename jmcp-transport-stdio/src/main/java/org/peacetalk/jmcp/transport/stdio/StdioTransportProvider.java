package org.peacetalk.jmcp.transport.stdio;

import org.peacetalk.jmcp.core.transport.McpTransport;
import org.peacetalk.jmcp.core.transport.TransportProvider;

/**
 * SPI implementation of TransportProvider for the stdio transport.
 * Discovered via java.util.ServiceLoader.
 */
public class StdioTransportProvider implements TransportProvider {

    @Override
    public String getName() {
        return "stdio";
    }

    @Override
    public McpTransport createTransport() {
        return new StdioTransport();
    }
}

