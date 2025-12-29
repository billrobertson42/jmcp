package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of describing a table structure
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TableDescription(
    String table,
    String schema,
    List<ColumnMetadata> columns,
    List<String> primaryKeys,
    List<IndexInfo> indexes
) {
}

