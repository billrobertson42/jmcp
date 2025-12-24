package org.peacetalk.jmcp.jdbc.tools.results;

import java.util.List;

/**
 * Result of describing a table structure
 */
public record TableDescription(
    String table,
    String schema,
    List<ColumnMetadata> columns,
    List<String> primaryKeys,
    List<IndexInfo> indexes
) {
}

