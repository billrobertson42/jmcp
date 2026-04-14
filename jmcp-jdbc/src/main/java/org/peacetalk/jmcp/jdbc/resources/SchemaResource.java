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
 * Resource representing a specific schema in a database connection.
 *
 * URI: db://connection/{id}/schema/{schemaName}
 *
 * Returns schema details with navigation links to tables, views, and other objects.
 */
public class SchemaResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final ConnectionManager connectionManager;

    public SchemaResource(String connectionId, String schemaName,
                          ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return schemaUri(connectionId, schemaName);
    }

    @Override
    public String getName() {
        return "Schema: " + schemaName;
    }

    @Override
    public String getDescription() {
        return "Schema with lists of tables, views, and procedures, including navigation URIs.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        List<TableLink> tables = new ArrayList<>();
        List<ViewLink> views = new ArrayList<>();
        List<ProcedureLink> procedures = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get tables
            try (ResultSet rs = metaData.getTables(null, schemaName, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.add(new TableLink(
                        tableName,
                        tableUri(connectionId, schemaName, tableName)
                    ));
                }
            }

            // Get views
            try (ResultSet rs = metaData.getTables(null, schemaName, "%", new String[]{"VIEW"})) {
                while (rs.next()) {
                    String viewName = rs.getString("TABLE_NAME");
                    views.add(new ViewLink(
                        viewName,
                        viewUri(connectionId, schemaName, viewName)
                    ));
                }
            }

            // Get procedures
            try (ResultSet rs = metaData.getProcedures(null, schemaName, "%")) {
                while (rs.next()) {
                    String procName = rs.getString("PROCEDURE_NAME");
                    short procType = rs.getShort("PROCEDURE_TYPE");
                    String type = switch (procType) {
                        case DatabaseMetaData.procedureReturnsResult -> "FUNCTION";
                        case DatabaseMetaData.procedureNoResult -> "PROCEDURE";
                        default -> "UNKNOWN";
                    };
                    procedures.add(new ProcedureLink(
                        procName,
                        type,
                        procedureUri(connectionId, schemaName, procName)
                    ));
                }
            }
        }

        SchemaResponse response = new SchemaResponse(
            schemaName,
            connectionId,
            tables,
            views,
            procedures,
            new NavigationLinks(
                connectionSchemasUri(connectionId),
                schemaRelationshipsUri(connectionId, schemaName)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record SchemaResponse(
        String name,
        String connectionId,
        List<TableLink> tables,
        List<ViewLink> views,
        List<ProcedureLink> procedures,
        NavigationLinks links
    ) {}

    /**
     * Table link with name and URI
     */
    public record TableLink(
        String name,
        String uri
    ) {}

    /**
     * View link with name and URI
     */
    public record ViewLink(
        String name,
        String uri
    ) {}

    /**
     * Procedure link with name, type, and URI
     */
    public record ProcedureLink(
        String name,
        String type,
        String uri
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent,
        String relationships
    ) {}
}

