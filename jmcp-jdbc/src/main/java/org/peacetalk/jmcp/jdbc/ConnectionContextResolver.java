package org.peacetalk.jmcp.jdbc;

/**
 * Interface for resolving connection contexts.
 * This allows JdbcToolAdapter to get connections without tight coupling to ConnectionManager.
 */
public interface ConnectionContextResolver {
    /**
     * Get the default connection ID
     */
    String getDefaultConnectionId();

    /**
     * Get a connection context by ID
     */
    ConnectionContext getConnectionContext(String connectionId);
}

