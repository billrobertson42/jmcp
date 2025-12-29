package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Result of previewing table rows
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TablePreviewResult(
    String table,
    String schema,
    List<ColumnMetadata> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    int limit
) {
}

