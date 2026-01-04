package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.BooleanProperty;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.ViewInfo;
import org.peacetalk.jmcp.jdbc.tools.results.ViewsListResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for listing database views with their definitions
 */
public class ListViewsTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "list-views";
    }

    @Override
    public String getDescription() {
        return "Lists all views with definitions in a given schemas.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "schema", new StringProperty("Optional schema/catalog name to filter views"),
                "includeDefinitions", new BooleanProperty("Include view SQL definitions (default: true)"),
                "database_id", new StringProperty("Optional database connection ID. If not provided, uses the default connection.")
            )
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String schemaPattern = params.has("schema") ? params.get("schema").asString() : null;
        boolean includeDefinitions = !params.has("includeDefinitions") || params.get("includeDefinitions").asBoolean();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();

            List<ViewInfo> views = new ArrayList<>();

            // Get views from metadata
            try (ResultSet rs = metaData.getTables(null, schemaPattern, "%", new String[]{"VIEW"})) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    String name = rs.getString("TABLE_NAME");

                    String definition = null;
                    List<String> columns = null;
                    Boolean isUpdatable = null;
                    String checkOption = null;

                    if (includeDefinitions) {
                        // Get view definition - database-specific query
                        definition = getViewDefinition(conn, productName, schema, name);

                        // Get columns
                        columns = getViewColumns(metaData, schema, name);

                        // Get updatable status - database-specific
                        isUpdatable = isViewUpdatable(conn, productName, schema, name);

                        // Get check option - database-specific
                        checkOption = getCheckOption(conn, productName, schema, name);
                    }

                    views.add(new ViewInfo(name, schema, definition, columns, isUpdatable, checkOption));
                }
            }

            return new ViewsListResult(views, views.size());
        }
    }

    private String getViewDefinition(Connection conn, String productName, String schema, String viewName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT definition FROM pg_views WHERE schemaname = ? AND viewname = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        } else if (productName.contains("oracle")) {
            query = "SELECT TEXT FROM ALL_VIEWS WHERE OWNER = ? AND VIEW_NAME = ?";
        } else if (productName.contains("sql server") || productName.contains("microsoft")) {
            query = "SELECT OBJECT_DEFINITION(OBJECT_ID(? + '.' + ?))";
        } else if (productName.contains("h2")) {
            query = "SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        } else {
            // Generic attempt using information_schema
            query = "SELECT VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        }

        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, viewName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            // Silently ignore if view definition not available
        }

        return null;
    }

    private List<String> getViewColumns(DatabaseMetaData metaData, String schema, String viewName) {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(null, schema, viewName, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            // Silently ignore
        }
        return columns.isEmpty() ? null : columns;
    }

    private Boolean isViewUpdatable(Connection conn, String productName, String schema, String viewName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT is_updatable FROM information_schema.views WHERE table_schema = ? AND table_name = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT IS_UPDATABLE FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        } else if (productName.contains("h2")) {
            query = "SELECT IS_UPDATABLE FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        } else {
            return null;
        }

        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, viewName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    return "YES".equalsIgnoreCase(value);
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }

        return null;
    }

    private String getCheckOption(Connection conn, String productName, String schema, String viewName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT check_option FROM information_schema.views WHERE table_schema = ? AND table_name = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT CHECK_OPTION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        } else if (productName.contains("h2")) {
            query = "SELECT CHECK_OPTION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        } else {
            return null;
        }

        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, viewName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    return (value != null && !value.equalsIgnoreCase("NONE")) ? value : null;
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }

        return null;
    }
}

