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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.peacetalk.jmcp.jdbc.resources.Util.*;

/**
 * Resource providing a complete foreign key relationship graph for a database connection.
 *
 * URI: db://connection/{id}/relationships
 *
 * This resource provides a comprehensive view of all foreign key relationships across
 * all schemas in the database. Useful for understanding database structure, generating
 * ER diagrams, and analyzing table dependencies.
 */
public class RelationshipsResource implements Resource {
    private final String connectionId;
    private final ConnectionManager connectionManager;

    public RelationshipsResource(String connectionId, ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return relationshipsUri(connectionId);
    }

    @Override
    public String getName() {
        return "Relationships for connection: " + connectionId;
    }

    @Override
    public String getDescription() {
        return "FK relationship graph with copyOrder (topological sort for dependency-safe data operations).";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);
        List<Relationship> relationships = new ArrayList<>();

        // For topological sort: map of "schema.table" -> dependencies
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        Set<String> allTables = new LinkedHashSet<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get all schemas
            List<String> schemas = new ArrayList<>();
            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    schemas.add(rs.getString("TABLE_SCHEM"));
                }
            }

            // For each schema, get all tables
            for (String schema : schemas) {
                try (ResultSet rs = metaData.getTables(null, schema, "%", new String[]{"TABLE"})) {
                    List<String> tables = new ArrayList<>();
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        tables.add(tableName);
                        String qualifiedName = schema + "." + tableName;
                        allTables.add(qualifiedName);
                        dependencyGraph.putIfAbsent(qualifiedName, new HashSet<>());
                    }

                    // For each table, get its foreign keys
                    for (String table : tables) {
                        String fromQualified = schema + "." + table;

                        try (ResultSet fkRs = metaData.getImportedKeys(null, schema, table)) {
                            String currentFkName = null;
                            List<ColumnMapping> currentMappings = new ArrayList<>();
                            String currentPkSchema = null;
                            String currentPkTable = null;

                            while (fkRs.next()) {
                                String fkName = fkRs.getString("FK_NAME");
                                String fkColumn = fkRs.getString("FKCOLUMN_NAME");
                                String pkSchema = fkRs.getString("PKTABLE_SCHEM");
                                String pkTable = fkRs.getString("PKTABLE_NAME");
                                String pkColumn = fkRs.getString("PKCOLUMN_NAME");

                                if (fkName == null) continue;

                                // Track dependency for topological sort
                                String toQualified = pkSchema + "." + pkTable;
                                dependencyGraph.computeIfAbsent(fromQualified, k -> new HashSet<>()).add(toQualified);

                                if (!fkName.equals(currentFkName)) {
                                    if (currentFkName != null) {
                                        relationships.add(new Relationship(
                                            currentFkName,
                                            schema,
                                            table,
                                            currentPkSchema,
                                            currentPkTable,
                                            new ArrayList<>(currentMappings),
                                            tableUri(connectionId, schema, table),
                                            tableUri(connectionId, currentPkSchema, currentPkTable)
                                        ));
                                    }
                                    currentFkName = fkName;
                                    currentMappings.clear();
                                    currentPkSchema = pkSchema;
                                    currentPkTable = pkTable;
                                }
                                currentMappings.add(new ColumnMapping(fkColumn, pkColumn));
                            }

                            if (currentFkName != null) {
                                relationships.add(new Relationship(
                                    currentFkName,
                                    schema,
                                    table,
                                    currentPkSchema,
                                    currentPkTable,
                                    currentMappings,
                                    tableUri(connectionId, schema, table),
                                    tableUri(connectionId, currentPkSchema, currentPkTable)
                                ));
                            }
                        }
                    }
                }
            }
        }

        // Compute topological sort for copy order
        TopologicalSortResult sortResult = TopologicalSort.sort(allTables, dependencyGraph);

        RelationshipsResponse response = new RelationshipsResponse(
            connectionId,
            relationships,
            sortResult.sortedTables(),
            sortResult.cycles(),
            new NavigationLinks(
                connectionUri(connectionId)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record RelationshipsResponse(
        String connectionId,
        List<Relationship> relationships,
        List<String> copyOrder,
        List<String> cyclesDetected,
        NavigationLinks links
    ) {}

    /**
     * A single foreign key relationship in the database
     */
    public record Relationship(
        String name,
        String fromSchema,
        String fromTable,
        String toSchema,
        String toTable,
        List<ColumnMapping> columns,
        String fromTableUri,
        String toTableUri
    ) {}

    /**
     * Column mapping for foreign keys
     */
    public record ColumnMapping(
        String fromColumn,
        String toColumn
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}

