package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Compact table preview result using arrays for row data.
 * Omits redundant fields like limit (can be inferred from row count).
 *
 * Format:
 * {
 *   "table": "products",
 *   "schema": "public",
 *   "cols": ["id", "name", "price"],
 *   "rows": [[1, "Widget", 19.99], [2, "Gadget", 29.99]],
 *   "count": 2
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompactTablePreviewResult(
    @JsonProperty("table") String table,
    @JsonProperty("schema") String schema,
    @JsonProperty("cols") List<String> columns,
    @JsonProperty("rows") List<List<Object>> rows,
    @JsonProperty("count") int count
) {
}

