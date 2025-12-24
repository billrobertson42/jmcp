package org.peacetalk.jmcp.jdbc.tools.results;

import java.util.List;

/**
 * Result of listing tables in a database
 */
public record TablesListResult(
    List<TableInfo> tables,
    int count
) {
}

