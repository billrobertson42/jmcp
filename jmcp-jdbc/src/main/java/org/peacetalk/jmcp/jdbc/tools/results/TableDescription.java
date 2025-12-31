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
    List<ForeignKeyInfo> foreignKeys,
    List<IndexInfo> indexes,
    // Optional extended information
    List<TriggerInfo> triggers,
    List<CheckConstraintInfo> checkConstraints,
    TableStatistics statistics,
    PartitionInfo partitions
) {
}

