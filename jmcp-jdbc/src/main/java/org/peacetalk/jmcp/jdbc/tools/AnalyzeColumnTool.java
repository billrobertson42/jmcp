package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.IntegerProperty;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.ColumnAnalysis;
import org.peacetalk.jmcp.jdbc.tools.results.ValueFrequency;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for analyzing a column to understand its data distribution and characteristics.
 *
 * Provides statistical analysis including distinct values, null count, min/max,
 * and most common values. Useful for data profiling and quality assessment.
 */
public class AnalyzeColumnTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TOP_VALUES_COUNT = 10;
    private static final int MAX_TOP_VALUES_COUNT = 50;

    @Override
    public String getName() {
        return "analyze-column";
    }

    @Override
    public String getDescription() {
        return "Analyze column data: distinct count, nulls, min/max, top values with frequencies.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "table", new StringProperty("Table name"),
                "column", new StringProperty("Column name to analyze"),
                "schema", new StringProperty("Schema name (optional, uses default)"),
                "top_values", new IntegerProperty("Most common values to return (default: " + DEFAULT_TOP_VALUES_COUNT + ", max: " + MAX_TOP_VALUES_COUNT + ")"),
                "database_id", new StringProperty("Database connection ID (optional, uses default)")
            ),
            List.of("table", "column")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String tableName = params.get("table").asString();
        String columnName = params.get("column").asString();
        String schemaName = params.has("schema") ? params.get("schema").asString() : null;
        int topValuesCount = params.has("top_values") ? params.get("top_values").asInt() : DEFAULT_TOP_VALUES_COUNT;

        // Validate top values count
        if (topValuesCount <= 0 || topValuesCount > MAX_TOP_VALUES_COUNT) {
            throw new IllegalArgumentException("top_values must be between 1 and " + MAX_TOP_VALUES_COUNT);
        }

        try (Connection conn = context.getConnection()) {
            // Resolve schema name
            schemaName = JdbcToolUtils.resolveSchemaName(conn, schemaName);

            // Validate table exists
            JdbcToolUtils.validateTableExists(conn, schemaName, tableName);

            // Validate column exists and get the actual column name as stored in the database
            String actualColumnName = JdbcToolUtils.validateColumnExists(conn, schemaName, tableName, columnName);

            String qualifiedTable = JdbcToolUtils.buildQualifiedTableName(schemaName, tableName);

            // Get basic statistics using the actual column name
            ColumnStats stats = getBasicStats(conn, qualifiedTable, actualColumnName);

            // Get top values with frequencies using the actual column name
            List<ValueFrequency> topValues = getTopValues(conn, qualifiedTable, actualColumnName, topValuesCount);

            return new ColumnAnalysis(
                tableName,
                schemaName,
                columnName,  // Return the original column name as requested
                stats.totalRows(),
                stats.distinctCount(),
                stats.nullCount(),
                stats.minValue(),
                stats.maxValue(),
                topValues
            );
        }
    }

    /**
     * Get basic statistics for the column.
     */
    private ColumnStats getBasicStats(Connection conn, String qualifiedTable, String columnName) throws SQLException {
        String sql = "SELECT " +
                    "COUNT(*) as total_rows, " +
                    "COUNT(DISTINCT " + columnName + ") as distinct_count, " +
                    "COUNT(*) - COUNT(" + columnName + ") as null_count, " +
                    "MIN(" + columnName + ") as min_value, " +
                    "MAX(" + columnName + ") as max_value " +
                    "FROM " + qualifiedTable;

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new ColumnStats(
                    rs.getLong("total_rows"),
                    rs.getLong("distinct_count"),
                    rs.getLong("null_count"),
                    objectToString(rs.getObject("min_value")),
                    objectToString(rs.getObject("max_value"))
                );
            }
        }

        throw new SQLException("Failed to retrieve column statistics");
    }

    /**
     * Get the most common values with their frequencies.
     */
    private List<ValueFrequency> getTopValues(Connection conn, String qualifiedTable, String columnName, int limit)
            throws SQLException {
        // Note: "value" is quoted because it's a reserved word in H2
        String sql = "SELECT " + columnName + " as \"value\", COUNT(*) as frequency " +
                    "FROM " + qualifiedTable + " " +
                    "WHERE " + columnName + " IS NOT NULL " +
                    "GROUP BY " + columnName + " " +
                    "ORDER BY frequency DESC " +
                    "LIMIT " + limit;

        List<ValueFrequency> topValues = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                topValues.add(new ValueFrequency(
                    objectToString(rs.getObject("value")),
                    rs.getLong("frequency")
                ));
            }
        }

        return topValues;
    }

    /**
     * Convert object to string representation, handling nulls.
     */
    private String objectToString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    /**
     * Escape identifier for SQL (basic implementation using double quotes).
     */
    private String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        // SQL standard identifier escaping
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * Internal record for basic statistics.
     */
    private record ColumnStats(
        long totalRows,
        long distinctCount,
        long nullCount,
        String minValue,
        String maxValue
    ) {}
}

