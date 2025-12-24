package org.peacetalk.jmcp.jdbc.tools.results;

/**
 * Information about a database index
 */
public record IndexInfo(
    String name,
    String column,
    boolean unique
) {
}

