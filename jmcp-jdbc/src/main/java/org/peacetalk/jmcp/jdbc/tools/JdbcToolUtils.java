package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.jdbc.tools.results.ColumnMetadata;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for JDBC tools to avoid code duplication.
 */
public final class JdbcToolUtils {

    private JdbcToolUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Resolve the schema name - use provided schema or fall back to connection's default schema.
     *
     * @param connection The database connection
     * @param providedSchema The schema name provided by the user (may be null)
     * @return The resolved schema name (may still be null if connection has no default schema)
     * @throws SQLException if there's an error getting the connection schema
     */
    public static String resolveSchemaName(Connection connection, String providedSchema) throws SQLException {
        if (providedSchema != null) {
            return providedSchema;
        }
        return connection.getSchema();
    }

    /**
     * Validate that a table exists in the database using DatabaseMetaData.
     * This prevents SQL injection by verifying the table name against actual database metadata.
     *
     * @param connection The database connection
     * @param schemaName The schema name (may be null)
     * @param tableName The table name to validate
     * @throws SQLException if the table does not exist or there's a database error
     */
    public static void validateTableExists(Connection connection, String schemaName, String tableName)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        boolean tableExists = false;

        try (ResultSet rs = metaData.getTables(null, schemaName, tableName, new String[]{"TABLE", "VIEW"})) {
            tableExists = rs.next();
        }

        if (!tableExists) {
            throw new SQLException("Table '" + tableName + "' does not exist" +
                (schemaName != null ? " in schema '" + schemaName + "'" : ""));
        }
    }

    /**
     * Build a qualified table name (schema.table or just table if schema is null).
     *
     * @param schemaName The schema name (may be null)
     * @param tableName The table name
     * @return The fully qualified table name
     */
    public static String buildQualifiedTableName(String schemaName, String tableName) {
        return schemaName != null ? schemaName + "." + tableName : tableName;
    }

    /**
     * Extract column metadata from a ResultSet.
     *
     * @param rs The ResultSet to extract metadata from
     * @return List of ColumnMetadata objects
     * @throws SQLException if there's an error accessing the metadata
     */
    public static List<ColumnMetadata> extractColumnMetadata(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<ColumnMetadata> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(new ColumnMetadata(
                metaData.getColumnName(i),
                metaData.getColumnTypeName(i)
            ));
        }
        return columns;
    }

    /**
     * Extract all rows from a ResultSet as a list of maps.
     *
     * @param rs The ResultSet to extract rows from
     * @param maxRows Maximum number of rows to extract (0 or negative for no limit)
     * @return List of row maps where keys are column names and values are column values
     * @throws SQLException if there's an error reading the ResultSet
     */
    public static List<Map<String, Object>> extractRows(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;

        while (rs.next() && (maxRows <= 0 || rowCount < maxRows)) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            rows.add(row);
            rowCount++;
        }

        return rows;
    }

    /**
     * Extract all rows from a ResultSet as a list of maps (no row limit).
     *
     * @param rs The ResultSet to extract rows from
     * @return List of row maps where keys are column names and values are column values
     * @throws SQLException if there's an error reading the ResultSet
     */
    public static List<Map<String, Object>> extractRows(ResultSet rs) throws SQLException {
        return extractRows(rs, 0);
    }

    /**
     * Extract rows from a ResultSet as a list of arrays (compact format).
     * This is more token-efficient than map format as column names aren't repeated.
     *
     * @param rs The ResultSet to extract rows from
     * @param maxRows Maximum number of rows to extract (0 or negative for no limit)
     * @return List of row arrays where each array contains column values in order
     * @throws SQLException if there's an error reading the ResultSet
     */
    public static List<List<Object>> extractRowsAsArrays(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<List<Object>> rows = new ArrayList<>();
        int rowCount = 0;

        while (rs.next() && (maxRows <= 0 || rowCount < maxRows)) {
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
            rowCount++;
        }

        return rows;
    }

    /**
     * Extract all rows from a ResultSet as arrays (no row limit).
     *
     * @param rs The ResultSet to extract rows from
     * @return List of row arrays
     * @throws SQLException if there's an error reading the ResultSet
     */
    public static List<List<Object>> extractRowsAsArrays(ResultSet rs) throws SQLException {
        return extractRowsAsArrays(rs, 0);
    }

    /**
     * Extract column names from a ResultSet.
     *
     * @param rs The ResultSet to extract column names from
     * @return List of column names in order
     * @throws SQLException if there's an error accessing the metadata
     */
    public static List<String> extractColumnNames(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<String> names = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            names.add(metaData.getColumnName(i));
        }
        return names;
    }
}

