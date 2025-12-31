package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.schema.BooleanProperty;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.JdbcTool;
import org.peacetalk.jmcp.jdbc.tools.results.ProcedureInfo;
import org.peacetalk.jmcp.jdbc.tools.results.ProcedureParameter;
import org.peacetalk.jmcp.jdbc.tools.results.ProceduresListResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool for listing stored procedures and functions
 */
public class ListProceduresTool implements JdbcTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "list-procedures";
    }

    @Override
    public String getDescription() {
        return "Lists stored procedures and functions in a schema. Use to discover callable routines and their definitions.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "schema", new StringProperty("Optional schema/catalog name to filter procedures"),
                "includeDetails", new BooleanProperty("Include parameters and definitions (default: true)"),
                "database_id", new StringProperty("Optional database connection ID. If not provided, uses the default connection.")
            )
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params, ConnectionContext context) throws Exception {
        String schemaPattern = params.has("schema") ? params.get("schema").asString() : null;
        boolean includeDetails = !params.has("includeDetails") || params.get("includeDetails").asBoolean();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();

            List<ProcedureInfo> procedures = new ArrayList<>();

            try (ResultSet rs = metaData.getProcedures(null, schemaPattern, "%")) {
                while (rs.next()) {
                    String schema = rs.getString("PROCEDURE_SCHEM");
                    String name = rs.getString("PROCEDURE_NAME");
                    short procType = rs.getShort("PROCEDURE_TYPE");
                    String remarks = rs.getString("REMARKS");

                    String type = switch (procType) {
                        case DatabaseMetaData.procedureReturnsResult -> "FUNCTION";
                        case DatabaseMetaData.procedureNoResult -> "PROCEDURE";
                        default -> "UNKNOWN";
                    };

                    List<ProcedureParameter> parameters = null;
                    String returnType = null;
                    String definition = null;
                    String language = null;
                    Boolean isDeterministic = null;

                    if (includeDetails) {
                        parameters = getProcedureParameters(metaData, schema, name);

                        if ("FUNCTION".equals(type) && parameters != null && !parameters.isEmpty()) {
                            ProcedureParameter returnParam = parameters.stream()
                                .filter(p -> "RETURN".equals(p.mode()))
                                .findFirst()
                                .orElse(null);
                            if (returnParam != null) {
                                returnType = returnParam.type();
                            }
                        }

                        definition = getProcedureDefinition(conn, productName, schema, name);
                        language = getProcedureLanguage(conn, productName, schema, name);
                        isDeterministic = isProcedureDeterministic(conn, productName, schema, name);
                    }

                    procedures.add(new ProcedureInfo(
                        name,
                        schema,
                        type,
                        parameters,
                        returnType,
                        definition,
                        language,
                        isDeterministic,
                        remarks != null && !remarks.isEmpty() ? remarks : null
                    ));
                }
            }

            return new ProceduresListResult(procedures, procedures.size());
        }
    }

    private List<ProcedureParameter> getProcedureParameters(DatabaseMetaData metaData, String schema, String procedureName) {
        List<ProcedureParameter> parameters = new ArrayList<>();

        try (ResultSet rs = metaData.getProcedureColumns(null, schema, procedureName, "%")) {
            while (rs.next()) {
                String paramName = rs.getString("COLUMN_NAME");
                String paramType = rs.getString("TYPE_NAME");
                short columnType = rs.getShort("COLUMN_TYPE");
                int position = rs.getInt("ORDINAL_POSITION");

                String mode = switch (columnType) {
                    case DatabaseMetaData.procedureColumnIn -> "IN";
                    case DatabaseMetaData.procedureColumnOut -> "OUT";
                    case DatabaseMetaData.procedureColumnInOut -> "INOUT";
                    case DatabaseMetaData.procedureColumnReturn -> "RETURN";
                    case DatabaseMetaData.procedureColumnResult -> "RESULT";
                    default -> "UNKNOWN";
                };

                parameters.add(new ProcedureParameter(paramName, paramType, mode, position));
            }
        } catch (Exception e) {
            // Silently ignore
        }

        return parameters.isEmpty() ? null : parameters;
    }

    private String getProcedureDefinition(Connection conn, String productName, String schema, String procName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT pg_get_functiondef(p.oid) FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ? AND p.proname = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT ROUTINE_DEFINITION FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = ? AND ROUTINE_NAME = ?";
        } else if (productName.contains("oracle")) {
            query = "SELECT TEXT FROM ALL_SOURCE WHERE OWNER = ? AND NAME = ? ORDER BY LINE";
        } else if (productName.contains("sql server") || productName.contains("microsoft")) {
            query = "SELECT OBJECT_DEFINITION(OBJECT_ID(? + '.' + ?))";
        } else {
            return null;
        }

        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, procName);
            try (ResultSet rs = stmt.executeQuery()) {
                StringBuilder def = new StringBuilder();
                while (rs.next()) {
                    String text = rs.getString(1);
                    if (text != null) {
                        def.append(text);
                    }
                }
                return def.length() > 0 ? def.toString() : null;
            }
        } catch (Exception e) {
            // Silently ignore
        }

        return null;
    }

    private String getProcedureLanguage(Connection conn, String productName, String schema, String procName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT l.lanname FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid JOIN pg_language l ON p.prolang = l.oid WHERE n.nspname = ? AND p.proname = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            return "SQL";
        } else {
            return null;
        }

        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, procName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }

        return null;
    }

    private Boolean isProcedureDeterministic(Connection conn, String productName, String schema, String procName) {
        String query = null;

        if (productName.contains("postgresql")) {
            query = "SELECT p.provolatile = 'i' FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = ? AND p.proname = ?";
        } else if (productName.contains("mysql") || productName.contains("mariadb")) {
            query = "SELECT IS_DETERMINISTIC = 'YES' FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = ? AND ROUTINE_NAME = ?";
        } else {
            return null;
        }

        try (var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, procName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }

        return null;
    }
}

