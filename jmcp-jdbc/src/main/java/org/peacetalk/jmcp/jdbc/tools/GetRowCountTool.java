package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.RowCountResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Tool for getting row count of a table
 */
public class GetRowCountTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "get-row-count";
    }

    @Override
    public String getDescription() {
        return "Get exact row count for a table.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "table", new StringProperty("Table name"),
                "schema", new StringProperty("Schema name (optional, uses default)"),
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

        try (Connection conn = context.getConnection()) {
            // Resolve schema name (use provided or default)
            schemaName = JdbcToolUtils.resolveSchemaName(conn, schemaName);

            // Validate table exists to prevent SQL injection
            JdbcToolUtils.validateTableExists(conn, schemaName, tableName);

            // Build qualified table name
            String fullTableName = JdbcToolUtils.buildQualifiedTableName(schemaName, tableName);

            String sql = "SELECT COUNT(*) FROM " + fullTableName;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                if (rs.next()) {
                    long count = rs.getLong(1);
                    return new RowCountResult(tableName, schemaName, count);
                }

                throw new SQLException("Could not get row count");
            }
        }
    }
}

