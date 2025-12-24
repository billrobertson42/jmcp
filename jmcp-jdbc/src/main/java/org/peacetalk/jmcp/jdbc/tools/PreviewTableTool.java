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
                "limit", new IntegerProperty("Number of rows to return (default 10, max 100)", 1, MAX_LIMIT)
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

        String fullTableName = schemaName != null ?
            schemaName + "." + tableName : tableName;

        // Note: LIMIT syntax varies by database, but most support this
        String sql = "SELECT * FROM " + fullTableName + " LIMIT " + limit;

        try (Connection conn = context.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Add column metadata
            List<ColumnMetadata> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(new ColumnMetadata(
                    metaData.getColumnName(i),
                    metaData.getColumnTypeName(i)
                ));
            }

            // Add rows
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                rows.add(row);
            }

            return new TablePreviewResult(tableName, schemaName, columns, rows, rows.size(), limit);
        }
    }
}

