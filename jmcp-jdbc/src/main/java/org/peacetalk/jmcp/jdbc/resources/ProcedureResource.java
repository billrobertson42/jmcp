/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.peacetalk.jmcp.jdbc.resources.Util.*;

/**
 * Resource representing a specific stored procedure or function in a schema.
 *
 * URI: db://connection/{id}/schema/{schema}/procedure/{procedure}
 *
 * Returns detailed procedure/function information including parameters, definition, and metadata.
 */
public class ProcedureResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final String procedureName;
    private final ConnectionManager connectionManager;

    public ProcedureResource(String connectionId, String schemaName, String procedureName,
                             ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.procedureName = procedureName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return procedureUri(connectionId, schemaName, procedureName);
    }

    @Override
    public String getName() {
        return "Procedure: " + procedureName;
    }

    @Override
    public String getDescription() {
        return "Stored procedure or function with parameters, definition, and metadata.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        String type = null;
        String remarks = null;
        String returnType = null;
        String definition = null;
        String language = null;
        Boolean isDeterministic = null;
        List<ParameterInfo> parameters = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();

            // Get procedure metadata
            try (ResultSet rs = metaData.getProcedures(null, schemaName, procedureName)) {
                if (rs.next()) {
                    short procType = rs.getShort("PROCEDURE_TYPE");
                    type = switch (procType) {
                        case DatabaseMetaData.procedureReturnsResult -> "FUNCTION";
                        case DatabaseMetaData.procedureNoResult -> "PROCEDURE";
                        default -> "UNKNOWN";
                    };
                    remarks = rs.getString("REMARKS");
                }
            }

            // Get procedure parameters
            try (ResultSet rs = metaData.getProcedureColumns(null, schemaName, procedureName, "%")) {
                while (rs.next()) {
                    String paramName = rs.getString("COLUMN_NAME");
                    String paramType = rs.getString("TYPE_NAME");
                    short columnType = rs.getShort("COLUMN_TYPE");
                    int position = rs.getInt("ORDINAL_POSITION");
                    Integer length = rs.getInt("LENGTH");
                    Integer precision = rs.getInt("PRECISION");
                    Integer scale = rs.getInt("SCALE");

                    String mode = switch (columnType) {
                        case DatabaseMetaData.procedureColumnIn -> "IN";
                        case DatabaseMetaData.procedureColumnOut -> "OUT";
                        case DatabaseMetaData.procedureColumnInOut -> "INOUT";
                        case DatabaseMetaData.procedureColumnReturn -> "RETURN";
                        case DatabaseMetaData.procedureColumnResult -> "RESULT";
                        default -> "UNKNOWN";
                    };

                    parameters.add(new ParameterInfo(
                        paramName,
                        paramType,
                        mode,
                        position,
                        length > 0 ? length : null,
                        precision > 0 ? precision : null,
                        scale > 0 ? scale : null
                    ));

                    // Capture return type if this is a function
                    if ("RETURN".equals(mode) && "FUNCTION".equals(type)) {
                        returnType = paramType;
                    }
                }
            }

            // Get procedure definition
            definition = getProcedureDefinition(conn, productName, schemaName, procedureName);

            // Get procedure language
            language = getProcedureLanguage(conn, productName, schemaName, procedureName);

            // Get deterministic flag
            isDeterministic = isProcedureDeterministic(conn, productName, schemaName, procedureName);
        }

        ProcedureResponse response = new ProcedureResponse(
            procedureName,
            schemaName,
            connectionId,
            type,
            parameters.isEmpty() ? null : parameters,
            returnType,
            definition,
            language,
            isDeterministic,
            remarks != null && !remarks.isEmpty() ? remarks : null,
            new NavigationLinks(
                schemaUri(connectionId, schemaName)
            )
        );

        return MAPPER.writeValueAsString(response);
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
            if (productName.contains("sql server") || productName.contains("microsoft")) {
                stmt.setString(1, schema);
                stmt.setString(2, procName);
            } else {
                stmt.setString(1, schema);
                stmt.setString(2, procName);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                StringBuilder def = new StringBuilder();
                while (rs.next()) {
                    String text = rs.getString(1);
                    if (text != null) {
                        def.append(text);
                        if (productName.contains("oracle")) {
                            def.append("\n");
                        }
                    }
                }
                return def.length() > 0 ? def.toString() : null;
            }
        } catch (Exception e) {
            // Silently ignore - not all databases support this
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

    /**
     * Response record for procedure details
     */
    public record ProcedureResponse(
        String name,
        String schema,
        String connectionId,
        String type,
        List<ParameterInfo> parameters,
        String returnType,
        String definition,
        String language,
        Boolean isDeterministic,
        String remarks,
        NavigationLinks links
    ) {}

    /**
     * Parameter information with mode and position
     */
    public record ParameterInfo(
        String name,
        String type,
        String mode,
        Integer position,
        Integer length,
        Integer precision,
        Integer scale
    ) {}

    /**
     * Navigation links
     */
    public record NavigationLinks(
        String parent
    ) {}
}

