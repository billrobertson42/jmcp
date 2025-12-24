package org.peacetalk.jmcp.jdbc.tools.results;

/**
 * Information about a database table
 */
public record TableInfo(
    String schema,
    String name,
    String type,
    String remarks
) {

}

