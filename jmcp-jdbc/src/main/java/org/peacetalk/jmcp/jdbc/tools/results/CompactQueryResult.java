package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Compact query result using arrays instead of objects for row data.
 * This significantly reduces token usage for LLM responses.
 *
 * Format:
 * {
 *   "table": "users",
 *   "schema": "public",
 *   "cols": ["col1", "col2", "col3"],
 *   "rows": [[val1, val2, val3], [val1, val2, val3]],
 *   "count": 10,
 *   "more": true
 * }
 *
 * Token savings vs verbose format: ~40-60% for typical result sets
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompactQueryResult(
    String table,
    String schema,
    @JsonProperty("cols") List<String> columns,
    @JsonProperty("rows") List<List<Object>> rows,
    @JsonProperty("count") int count,
    @JsonProperty("more") boolean hasMore
) {
    // Constructor for query tool (no table/schema context)
    public CompactQueryResult(List<String> columns, List<List<Object>> rows, int count, boolean hasMore) {
        this(null, null, columns, rows, count, hasMore);
    }

    // Constructor for sample tool (with table/schema context)
    public CompactQueryResult(String table, String schema, List<String> columns, List<List<Object>> rows, int count) {
        this(table, schema, columns, rows, count, false);
    }
}

