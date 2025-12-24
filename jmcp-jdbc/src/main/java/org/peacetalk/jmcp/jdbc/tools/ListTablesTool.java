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
        return "List all tables in the database or a specific schema";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "schema", new StringProperty("Optional schema/catalog name to filter tables")
            )
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String schemaPattern = params.has("schema") ? params.get("schema").asText() : null;

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

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

