package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.TableInfo;
import org.peacetalk.jmcp.jdbc.tools.results.TablesListResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for listing tables in a database
 */
public class ListTablesTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "list-tables";
    }

    @Override
    public String getDescription() {
        return "Lists all tables in a given schema. If no schema is specified, lists tables in the connection's default schema. Use this to discover available tables.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "schema", new StringProperty("Optional schema/catalog name to filter tables"),
                "database_id", new StringProperty("Optional database connection ID. If not provided, uses the default connection.")
            )
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get schema pattern: use provided schema, or default to connection's current schema
            String schemaPattern;
            if (params.has("schema")) {
                schemaPattern = params.get("schema").asString();
            } else {
                // Get the default schema for this connection
                schemaPattern = conn.getSchema();
            }

            List<TableInfo> tables = new ArrayList<>();

            try (ResultSet rs = metaData.getTables(null, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    String name = rs.getString("TABLE_NAME");
                    String type = rs.getString("TABLE_TYPE");
                    String remarks = rs.getString("REMARKS");

                    tables.add(new TableInfo(
                        schema,
                        name,
                        type,
                        (remarks != null && !remarks.isEmpty()) ? remarks : null
                    ));
                }
            }

            return new TablesListResult(tables, tables.size());
        }
    }
}

