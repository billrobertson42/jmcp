package org.peacetalk.jmcp.jdbc.tools.results;

import java.util.List;
import java.util.Map;

/**
 * Result of previewing table rows
 */
public record TablePreviewResult(
    String table,
    String schema,
    List<ColumnMetadata> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    int limit
) {
}

