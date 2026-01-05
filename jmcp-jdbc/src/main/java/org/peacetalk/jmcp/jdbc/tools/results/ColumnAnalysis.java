package org.peacetalk.jmcp.jdbc.tools.results;

import java.util.List;

/**
 * Result of analyzing a column's data distribution.
 *
 * Provides comprehensive statistics about a column including:
 * - Total row count
 * - Distinct value count
 * - Null count
 * - Min/max values
 * - Most common values with frequencies
 */
public record ColumnAnalysis(
    String table,
    String schema,
    String column,
    long totalRows,
    long distinctCount,
    long nullCount,
    String minValue,
    String maxValue,
    List<ValueFrequency> topValues
) {}

