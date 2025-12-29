package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.IntegerProperty;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.ColumnMetadata;
import org.peacetalk.jmcp.jdbc.tools.results.TablePreviewResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for previewing first N rows of a table
 */
public class PreviewTableTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    @Override
    public String getName() {
        return "preview-table";
    }

    @Override
    public String getDescription() {
        return "Get the first N rows from a table for preview (default 10, max 100)";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "table", new StringProperty("The table name"),
                "schema", new StringProperty("Optional schema name"),
                "limit", new IntegerProperty("Number of rows to return (default 10, max 100)", 1, MAX_LIMIT),
                "database_id", new StringProperty("Optional database connection ID. If not provided, uses the default connection.")
            ),
            List.of("table")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String tableName = params.get("table").asString();
        String schemaName = params.has("schema") ? params.get("schema").asString() : null;
        int limit = params.has("limit") ? params.get("limit").asInt() : DEFAULT_LIMIT;

        // Enforce max limit
        limit = Math.min(limit, MAX_LIMIT);

        try (Connection conn = context.getConnection()) {
            // If schema is not specified, try to use the default schema
            if (schemaName == null) {
                schemaName = conn.getSchema();
            }

            // Validate that the table exists using DatabaseMetaData to prevent SQL injection
            DatabaseMetaData metaData = conn.getMetaData();
            boolean tableExists = false;

            try (ResultSet rs = metaData.getTables(null, schemaName, tableName, new String[]{"TABLE", "VIEW"})) {
                tableExists = rs.next();
            }

            if (!tableExists) {
                throw new java.sql.SQLException("Table '" + tableName + "' does not exist" +
                    (schemaName != null ? " in schema '" + schemaName + "'" : ""));
            }

            // Now safe to construct SQL with validated table name
            String fullTableName = schemaName != null ?
                schemaName + "." + tableName : tableName;

            // Note: LIMIT syntax varies by database, but most support this
            String sql = "SELECT * FROM " + fullTableName + " LIMIT " + limit;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData rsMetaData = rs.getMetaData();
                int columnCount = rsMetaData.getColumnCount();

                // Add column metadata
                List<ColumnMetadata> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(new ColumnMetadata(
                        rsMetaData.getColumnName(i),
                        rsMetaData.getColumnTypeName(i)
                    ));
                }

                // Add rows
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rsMetaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    rows.add(row);
                }

                return new TablePreviewResult(tableName, schemaName, columns, rows, rows.size(), limit);
            }
        }
    }
}

