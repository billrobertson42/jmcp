package org.peacetalk.jmcp.jdbc.tools.results;

/**
 * Information about a database schema
 */
public record SchemaInfo(
    String name,
    String catalog
) {
}

