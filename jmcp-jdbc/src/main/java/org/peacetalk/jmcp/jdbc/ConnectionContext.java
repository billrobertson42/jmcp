package org.peacetalk.jmcp.jdbc;

import java.sql.Connection;

/**
 * Context for executing JDBC operations
 */
@FunctionalInterface
public interface ConnectionContext {
    /**
     * Get a connection from the pool
     */
    Connection getConnection() throws Exception;

}

