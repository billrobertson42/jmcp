package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.ArrayProperty;
import org.peacetalk.jmcp.core.schema.BooleanProperty;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.CompactQueryResult;
import org.peacetalk.jmcp.jdbc.validation.ReadOnlySqlValidator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        return "Execute read-only SELECT query. Returns up to " + MAX_ROWS + " rows. Use validate_only=true to check syntax without executing.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "sql", new StringProperty("SELECT query to execute"),
                "parameters", new ArrayProperty(
                    "Query parameters for prepared statement",
                    new StringProperty("Parameter value")
                ),
                "validate_only", new BooleanProperty("If true, validate SQL syntax without executing (default: false)"),
                "database_id", new StringProperty("Database connection ID (optional, uses default)")
            ),
            List.of("sql")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String sql = params.get("sql").asString().trim();
        boolean validateOnly = params.has("validate_only") && params.get("validate_only").asBoolean();

        // Validate SQL is read-only using robust parser
        ReadOnlySqlValidator.validateReadOnly(sql);

        List<String> parameters = new ArrayList<>();
        if (params.has("parameters") && params.get("parameters").isArray()) {
            params.get("parameters").forEach(p -> parameters.add(p.asString()));
        }

        try (Connection conn = context.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Set parameters for validation
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setString(i + 1, parameters.get(i));
            }

            // If validate_only, just return success without executing
            if (validateOnly) {
                return new ValidationResult(true, "Query syntax is valid");
            }

            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            stmt.setMaxRows(MAX_ROWS);

            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToJson(rs);
            }
        }
    }

    /**
     * Result for validate_only mode
     */
    public record ValidationResult(boolean valid, String message) {}

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

