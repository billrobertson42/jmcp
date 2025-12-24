package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.ColumnMetadata;
import org.peacetalk.jmcp.jdbc.tools.results.IndexInfo;
import org.peacetalk.jmcp.jdbc.tools.results.TableDescription;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for describing table structure (columns, types, constraints)
 */
public class DescribeTableTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "describe-table";
    }

    @Override
    public String getDescription() {
        return "Get detailed information about a table's structure including columns, types, and constraints";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "table", new StringProperty("The table name to describe"),
                "schema", new StringProperty("Optional schema name")
            ),
            List.of("table")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String tableName = params.get("table").asText();
        String schemaName = params.has("schema") ? params.get("schema").asText() : null;

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // If schema is not specified, try to use the default schema
            if (schemaName == null) {
                schemaName = conn.getSchema();
            }

            // Get columns
            List<ColumnMetadata> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(null, schemaName, tableName, "%")) {
                while (rs.next()) {
                    String defaultValue = rs.getString("COLUMN_DEF");
                    String remarks = rs.getString("REMARKS");

                    columns.add(new ColumnMetadata(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        defaultValue,
                        remarks != null && !remarks.isEmpty() ? remarks : null
                    ));
                }
            }

            // Get primary keys
            List<String> primaryKeys = new ArrayList<>();
            try (ResultSet rs = metaData.getPrimaryKeys(null, schemaName, tableName)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }

            // Get indexes
            List<IndexInfo> indexes = new ArrayList<>();
            try (ResultSet rs = metaData.getIndexInfo(null, schemaName, tableName, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName != null) {
                        indexes.add(new IndexInfo(
                            indexName,
                            rs.getString("COLUMN_NAME"),
                            !rs.getBoolean("NON_UNIQUE")
                        ));
                    }
                }
            }

            return new TableDescription(tableName, schemaName, columns, primaryKeys, indexes);
        }
    }
}

