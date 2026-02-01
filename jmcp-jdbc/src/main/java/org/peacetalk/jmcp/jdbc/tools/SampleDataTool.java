package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.IntegerProperty;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.CompactQueryResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Tool for sampling data from a table to preview actual data values.
 *
 * Provides smart sampling strategies to get representative data samples
 * without retrieving entire tables. Useful for data exploration and
 * understanding what actual values look like.
 */
public class SampleDataTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_SAMPLE_SIZE = 10;
    private static final int MAX_SAMPLE_SIZE = 100;

    @Override
    public String getName() {
        return "sample-data";
    }

    @Override
    public String getDescription() {
        return "Get sample rows from a table. Strategies: 'first', 'random', 'last'. Max " + MAX_SAMPLE_SIZE + " rows.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "table", new StringProperty("Table name"),
                "schema", new StringProperty("Schema name (optional, uses default)"),
                "sample_size", new IntegerProperty("Rows to sample (default: " + DEFAULT_SAMPLE_SIZE + ", max: " + MAX_SAMPLE_SIZE + ")"),
                "strategy", new StringProperty("Sampling strategy: 'first', 'random', 'last' (default: first)"),
                "columns", new StringProperty("Comma-separated column names (optional, default: all)"),
                "database_id", new StringProperty("Database connection ID (optional, uses default)")
            ),
            List.of("table")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String tableName = params.get("table").asString();
        String schemaName = params.has("schema") ? params.get("schema").asString() : null;
        int sampleSize = params.has("sample_size") ? params.get("sample_size").asInt() : DEFAULT_SAMPLE_SIZE;
        String strategy = params.has("strategy") ? params.get("strategy").asString().toLowerCase() : "first";
        String columns = params.has("columns") ? params.get("columns").asString() : "*";

        // Validate sample size
        if (sampleSize <= 0 || sampleSize > MAX_SAMPLE_SIZE) {
            throw new IllegalArgumentException("sample_size must be between 1 and " + MAX_SAMPLE_SIZE);
        }

        // Validate strategy
        if (!strategy.equals("first") && !strategy.equals("random") && !strategy.equals("last")) {
            throw new IllegalArgumentException("strategy must be 'first', 'random', or 'last'");
        }

        try (Connection conn = context.getConnection()) {
            // Resolve schema name
            schemaName = JdbcToolUtils.resolveSchemaName(conn, schemaName);

            // Validate table exists
            JdbcToolUtils.validateTableExists(conn, schemaName, tableName);

            // Build and execute query based on strategy
            String sql = buildSampleQuery(conn, schemaName, tableName, columns, sampleSize, strategy);

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                // Extract column names
                List<String> columnNames = JdbcToolUtils.extractColumnNames(rs);

                // Extract rows
                List<List<Object>> rows = JdbcToolUtils.extractRowsAsArrays(rs, sampleSize);

                // Return compact format with table and schema context
                return new CompactQueryResult(tableName, schemaName, columnNames, rows, rows.size());
            }
        }
    }

    /**
     * Build the appropriate SQL query based on sampling strategy.
     */
    private String buildSampleQuery(Connection conn, String schemaName, String tableName,
                                    String columns, int sampleSize, String strategy) throws SQLException {
        String qualifiedTable = JdbcToolUtils.buildQualifiedTableName(schemaName, tableName);
        String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();

        // Validate and sanitize column list
        String columnList = sanitizeColumnList(conn, schemaName, tableName, columns);

        return switch (strategy) {
            case "first" -> buildFirstRowsQuery(qualifiedTable, columnList, sampleSize);
            case "random" -> buildRandomSampleQuery(qualifiedTable, columnList, sampleSize, productName);
            case "last" -> buildLastRowsQuery(conn, schemaName, tableName, columnList, sampleSize, productName);
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        };
    }

    /**
     * Validate and sanitize column list to prevent SQL injection.
     * Validates each column name against table metadata.
     */
    private String sanitizeColumnList(Connection conn, String schemaName, String tableName, String columns)
            throws SQLException {
        if (columns == null || columns.trim().isEmpty() || columns.trim().equals("*")) {
            return "*";
        }

        // Split comma-separated column names and validate each
        String[] columnNames = columns.split(",");
        StringBuilder validatedColumns = new StringBuilder();

        for (int i = 0; i < columnNames.length; i++) {
            String columnName = columnNames[i].trim();

            // Validate column exists and get the actual column name as stored in the database
            String actualColumnName = JdbcToolUtils.validateColumnExists(conn, schemaName, tableName, columnName);

            if (i > 0) {
                validatedColumns.append(", ");
            }
            // Use the actual column name directly (no quoting needed for standard identifiers)
            validatedColumns.append(actualColumnName);
        }

        return validatedColumns.toString();
    }

    /**
     * Query for first N rows.
     */
    private String buildFirstRowsQuery(String qualifiedTable, String columns, int limit) {
        return "SELECT " + columns + " FROM " + qualifiedTable + " LIMIT " + limit;
    }

    /**
     * Query for random sample - database-specific.
     */
    private String buildRandomSampleQuery(String qualifiedTable, String columns, int limit, String productName) {
        if (productName.contains("postgresql") || productName.contains("h2") || productName.contains("sqlite")) {
            return "SELECT " + columns + " FROM " + qualifiedTable + " ORDER BY RANDOM() LIMIT " + limit;
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            return "SELECT " + columns + " FROM " + qualifiedTable + " ORDER BY RAND() LIMIT " + limit;
        } else if (productName.contains("sql server") || productName.contains("microsoft")) {
            return "SELECT TOP " + limit + " " + columns + " FROM " + qualifiedTable + " ORDER BY NEWID()";
        } else if (productName.contains("oracle")) {
            return "SELECT " + columns + " FROM (SELECT " + columns + " FROM " + qualifiedTable +
                   " ORDER BY DBMS_RANDOM.VALUE) WHERE ROWNUM <= " + limit;
        } else {
            // Fallback to first rows if random not supported
            return buildFirstRowsQuery(qualifiedTable, columns, limit);
        }
    }

    /**
     * Query for last N rows - requires primary key or unique column.
     * Falls back to DESC ordering if possible, or just first rows if not.
     */
    private String buildLastRowsQuery(Connection conn, String schemaName, String tableName,
                                     String columns, int limit, String productName) throws SQLException {
        // Try to find primary key columns
        String orderByClause = findPrimaryKeyOrderBy(conn, schemaName, tableName);

        if (orderByClause != null) {
            // Use primary key in descending order
            String qualifiedTable = JdbcToolUtils.buildQualifiedTableName(schemaName, tableName);
            return "SELECT " + columns + " FROM " + qualifiedTable +
                   " ORDER BY " + orderByClause + " DESC LIMIT " + limit;
        } else {
            // Fallback to first rows (can't determine order without PK)
            String qualifiedTable = JdbcToolUtils.buildQualifiedTableName(schemaName, tableName);
            return buildFirstRowsQuery(qualifiedTable, columns, limit);
        }
    }

    /**
     * Find primary key columns for ordering.
     */
    private String findPrimaryKeyOrderBy(Connection conn, String schemaName, String tableName) throws SQLException {
        StringBuilder orderBy = new StringBuilder();

        try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, schemaName, tableName)) {
            while (rs.next()) {
                if (orderBy.length() > 0) {
                    orderBy.append(", ");
                }
                orderBy.append(rs.getString("COLUMN_NAME"));
            }
        }

        return orderBy.length() > 0 ? orderBy.toString() : null;
    }
}

