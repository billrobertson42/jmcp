package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of getting row count for a table
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RowCountResult(
    String table,
    String schema,
    long rowCount
) {
}

