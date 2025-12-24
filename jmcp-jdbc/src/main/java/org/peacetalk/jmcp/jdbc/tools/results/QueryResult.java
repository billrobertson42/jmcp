package org.peacetalk.jmcp.jdbc.tools.results;

import java.util.List;
import java.util.Map;

/**
 * Result of executing a SQL query
 */
public record QueryResult(
    List<ColumnMetadata> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    boolean hasMore
) {
}

