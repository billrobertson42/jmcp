package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.SchemaInfo;
import org.peacetalk.jmcp.jdbc.tools.results.SchemasListResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for listing schemas/catalogs in a database
 */
public class ListSchemasTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "list-schemas";
    }

    @Override
    public String getDescription() {
        return "List all schemas/catalogs in the database";
    }

    @Override
    public JsonNode getInputSchema() {
        // No parameters required for this tool
        ObjectSchema schema = new ObjectSchema(Map.of());
        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            List<SchemaInfo> schemas = new ArrayList<>();

            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_SCHEM");
                    String catalog = rs.getString("TABLE_CATALOG");
                    schemas.add(new SchemaInfo(name, catalog));
                }
            }

            return new SchemasListResult(schemas, schemas.size());
        }
    }
}

