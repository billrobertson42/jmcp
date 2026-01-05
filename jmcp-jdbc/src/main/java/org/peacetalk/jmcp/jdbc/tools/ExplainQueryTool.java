package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.ExplainQueryResult;
import org.peacetalk.jmcp.jdbc.validation.ReadOnlySqlValidator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * Tool for getting query execution plans
 */
public class ExplainQueryTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "explain-query";
    }

    @Override
    public String getDescription() {
        return "Get the execution plan for a SELECT query to understand performance";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "sql", new StringProperty("SELECT query to explain"),
                "database_id", new StringProperty("Database connection ID (optional, uses default)")
            ),
            List.of("sql")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String sql = params.get("sql").asString().trim();

        // Validate SQL is read-only
        // This prevents explaining (and potentially executing) non-SELECT statements
        ReadOnlySqlValidator.validateReadOnly(sql);

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();

            String explainSql = buildExplainQuery(productName, sql);
            StringBuilder plan = new StringBuilder();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(explainSql)) {

                ResultSetMetaData rsMetaData = rs.getMetaData();
                int columnCount = rsMetaData.getColumnCount();

                // Build plan output
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String value = rs.getString(i);
                        if (value != null) {
                            if (plan.length() > 0) {
                                plan.append("\n");
                            }
                            plan.append(value);
                        }
                    }
                }
            }

            return new ExplainQueryResult(sql, plan.toString(), getFormatType(productName));
        }
    }

    private String buildExplainQuery(String productName, String sql) {
        return switch (productName) {
            case String s when s.contains("postgresql") -> "EXPLAIN (FORMAT TEXT) " + sql;
            case String s when s.contains("oracle") -> "EXPLAIN PLAN FOR " + sql;
            case String s when s.contains("sql server") || s.contains("microsoft") -> "SET SHOWPLAN_TEXT ON; " + sql;
            case String s when s.contains("sqlite") -> "EXPLAIN QUERY PLAN " + sql;
            default -> "EXPLAIN " + sql;
        };
    }

    private String getFormatType(String productName) {
        return switch (productName) {
            case String s when s.contains("postgresql") -> "PostgreSQL TEXT";
            case String s when s.contains("mysql") || s.contains("mariadb") -> "MySQL TRADITIONAL";
            case String s when s.contains("oracle") -> "Oracle BASIC";
            case String s when s.contains("sql server") -> "SQL Server TEXT";
            case String s when s.contains("h2") -> "H2";
            case String s when s.contains("sqlite") -> "SQLite QUERY PLAN";
            default -> "UNKNOWN";
        };
    }
}

