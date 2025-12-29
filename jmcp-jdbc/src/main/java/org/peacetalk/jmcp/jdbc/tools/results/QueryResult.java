package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Result of executing a SQL query
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryResult(
    List<ColumnMetadata> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    boolean hasMore
) {
}

