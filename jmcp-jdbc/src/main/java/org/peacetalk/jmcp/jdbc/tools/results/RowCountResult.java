package org.peacetalk.jmcp.jdbc.tools.results;

/**
 * Result of getting row count for a table
 */
public record RowCountResult(
    String table,
    String schema,
    long rowCount
) {
}

