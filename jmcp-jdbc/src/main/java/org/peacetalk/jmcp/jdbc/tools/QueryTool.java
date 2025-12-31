package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.ArrayProperty;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.CompactQueryResult;
import org.peacetalk.jmcp.jdbc.validation.ReadOnlySqlValidator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for executing SELECT queries
 */
public class QueryTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ROWS = 1000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    @Override
    public String getName() {
        return "query";
    }

    @Override
    public String getDescription() {
        return "Execute a read-only SQL SELECT query. Returns up to " + MAX_ROWS + " rows. " +
            "Returns compact format: {\"table\":\"name\", \"schema\":\"name\", \"cols\":[\"col_name1\",\"col_name2\",...], \"rows\":[[val1,val2,...],[...]], \"count\":N}. " +
            "Data in 'rows' as arrays where the order in each row matches the order of the columns.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "sql", new StringProperty("The SELECT query to execute"),
                "parameters", new ArrayProperty(
                    "Optional query parameters for prepared statement",
                    new StringProperty("Parameter value")
                ),
                "database_id", new StringProperty("Optional database connection ID. If not provided, uses the default connection.")
            ),
            List.of("sql")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String sql = params.get("sql").asString().trim();

        // Validate SQL is read-only using robust parser
        // This catches CTEs, subqueries, and all SQL variants correctly
        ReadOnlySqlValidator.validateReadOnly(sql);

        List<String> parameters = new ArrayList<>();
        if (params.has("parameters") && params.get("parameters").isArray()) {
            params.get("parameters").forEach(p -> parameters.add(p.asString()));
        }

        try (Connection conn = context.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            stmt.setMaxRows(MAX_ROWS);

            // Set parameters
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setString(i + 1, parameters.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToJson(rs);
            }
        }
    }

    private CompactQueryResult resultSetToJson(ResultSet rs) throws SQLException {
        // Extract column names
        List<String> columnNames = JdbcToolUtils.extractColumnNames(rs);

        // Extract rows as arrays (compact format - saves ~40-60% tokens)
        List<List<Object>> rows = JdbcToolUtils.extractRowsAsArrays(rs, MAX_ROWS);

        // Check if there are more rows
        boolean hasMore = rs.next();

        return new CompactQueryResult(columnNames, rows, rows.size(), hasMore);
    }
}

