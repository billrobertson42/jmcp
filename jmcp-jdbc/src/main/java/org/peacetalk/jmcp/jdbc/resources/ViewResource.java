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
import org.peacetalk.jmcp.jdbc.ConnectionSupplier;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.peacetalk.jmcp.jdbc.resources.Util.*;

/**
 * Resource representing a specific view in a schema.
 *
 * URI: db://connection/{id}/schema/{schema}/view/{view}
 *
 * Returns detailed view information including columns.
 */
public class ViewResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final String viewName;
    private final ConnectionManager connectionManager;

    public ViewResource(String connectionId, String schemaName, String viewName,
                        ConnectionManager connectionManager, ObjectMapper mapper) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.viewName = viewName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return viewUri(connectionId, schemaName, viewName);
    }

    @Override
    public String getName() {
        return "View: " + viewName;
    }

    @Override
    public String getDescription() {
        return "View structure: columns and SQL definition.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionSupplier context = connectionManager.getContext(connectionId);

        List<ColumnInfo> columns = new ArrayList<>();
        String viewRemarks = null;
        String viewDefinition = null;

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get view remarks
            try (ResultSet rs = metaData.getTables(null, schemaName, viewName, new String[]{"VIEW"})) {
                if (rs.next()) {
                    viewRemarks = rs.getString("REMARKS");
                }
            }

            // Get columns
            try (ResultSet rs = metaData.getColumns(null, schemaName, viewName, "%")) {
                while (rs.next()) {
                    columns.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("DECIMAL_DIGITS"),
                        "YES".equals(rs.getString("IS_NULLABLE")),
                        rs.getString("REMARKS")
                    ));
                }
            }

            // Get view definition - database-specific queries
            viewDefinition = getViewDefinition(conn, schemaName, viewName);
        }

        ViewResponse response = new ViewResponse(
            viewName,
            schemaName,
            connectionId,
            viewRemarks,
            viewDefinition,
            columns,
            new NavigationLinks(
                schemaUri(connectionId, schemaName)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Get the view definition SQL using database-specific queries.
     * Attempts multiple strategies to retrieve the view definition across different databases.
     */
    private String getViewDefinition(Connection conn, String schema, String view) {
        // Strategy 1: Try INFORMATION_SCHEMA.VIEWS (SQL standard, works for most databases)
        String sql1 = "SELECT VIEW_DEFINITION FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (var stmt = conn.prepareStatement(sql1)) {
            stmt.setString(1, schema);
            stmt.setString(2, view);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString(1);
                    if (def != null && !def.isEmpty()) {
                        return def;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and try next strategy
        }

        // Strategy 2: Try database-specific system tables
        // PostgreSQL: pg_views
        String sql2 = "SELECT definition FROM pg_views WHERE schemaname = ? AND viewname = ?";
        try (var stmt = conn.prepareStatement(sql2)) {
            stmt.setString(1, schema);
            stmt.setString(2, view);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString(1);
                    if (def != null && !def.isEmpty()) {
                        return def;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and try next strategy
        }

        // Strategy 3: MySQL - use SHOW CREATE VIEW
        String sql3 = "SHOW CREATE VIEW " + escapeIdentifier(schema) + "." + escapeIdentifier(view);
        try (var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql3)) {
            if (rs.next()) {
                // SHOW CREATE VIEW returns multiple columns, the view definition is usually in column 2
                String def = rs.getString(2);
                if (def != null && !def.isEmpty()) {
                    return def;
                }
            }
        } catch (Exception e) {
            // Ignore and try next strategy
        }

        // Strategy 4: H2 Database
        String sql4 = "SELECT SQL FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (var stmt = conn.prepareStatement(sql4)) {
            stmt.setString(1, schema);
            stmt.setString(2, view);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString(1);
                    if (def != null && !def.isEmpty()) {
                        return def;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore - unable to retrieve
        }

        return null; // Could not retrieve view definition
    }

    /**
     * Simple identifier escaping for SQL queries.
     */
    private String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        // Simple backtick escaping for MySQL-style databases
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * Response record for serialization
     */
    public record ViewResponse(
        String name,
        String schema,
        String connectionId,
        String remarks,
        String viewDefinition,
        List<ColumnInfo> columns,
        NavigationLinks links
    ) {}

    /**
     * Column information
     */
    public record ColumnInfo(
        String name,
        String dataType,
        int size,
        int decimalDigits,
        boolean nullable,
        String remarks
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}

