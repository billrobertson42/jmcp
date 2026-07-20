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

import org.peacetalk.jmcp.jdbc.ConnectionContext;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.peacetalk.jmcp.jdbc.resources.Util.tableUri;

/**
 * Shared foreign-key-relationship-graph engine for {@link RelationshipsResource}
 * (all visible schemas) and {@link SchemaRelationshipsResource} (one schema).
 *
 * Owns table enumeration, the {@code getImportedKeys}/{@code getExportedKeys}
 * walk (grouping rows by {@code FK_NAME}), the schema-visibility filter, and the
 * {@link TopologicalSort} invocation.
 *
 * The two resources are NOT unified onto one cross-schema rule -- they behave
 * differently today, and this class preserves each behavior exactly via the
 * {@code schemaName} parameter:
 *
 * <ul>
 *   <li>{@code schemaName == null} (RelationshipsResource, all visible schemas):
 *       every table's {@code getImportedKeys} is walked, and every discovered
 *       dependency is tracked in the topological sort, regardless of whether the
 *       referenced table's schema is itself visible.</li>
 *   <li>{@code schemaName != null} (SchemaRelationshipsResource, one schema):
 *       only that schema's tables are enumerated; both {@code getImportedKeys}
 *       (outgoing FKs) and {@code getExportedKeys} (incoming FKs from other
 *       schemas) are walked so cross-schema relationships are still reported,
 *       but only intra-schema dependencies (target schema equal to
 *       {@code schemaName}) are tracked in the topological sort -- cross-schema
 *       dependencies in either direction are excluded from {@code copyOrder}
 *       entirely, though they still appear in {@code relationships}.</li>
 * </ul>
 *
 * <p>The {@code getExportedKeys} path's {@link ColumnMapping} direction was
 * previously reversed relative to the relationship's own {@code fromTable}/
 * {@code toTable} direction -- fixed below; see {@link #walkExportedKeys}.
 */
final class RelationshipGraphBuilder {

    /**
     * @param relationships  every FK relationship found in scope, in discovery order
     * @param copyOrder      topologically-sorted qualified table names (dependency-safe order)
     * @param cyclesDetected qualified names of tables involved in a dependency cycle, or null if none
     */
    record Graph(List<Relationship> relationships, List<String> copyOrder, List<String> cyclesDetected) {}

    /** One row of a getImportedKeys/getExportedKeys result, grouped by FK_NAME. */
    private record FkRow(String column, String otherSchema, String otherTable, String otherColumn) {}

    private RelationshipGraphBuilder() {}

    /**
     * @param connectionId used only to build the {@code fromTableUri}/{@code toTableUri}
     *                     navigation links embedded in each {@link Relationship}
     * @param context      connection source; also the schema-visibility filter when
     *                     {@code schemaName} is null
     * @param schemaName   null to build the graph across all visible schemas
     *                     (RelationshipsResource); a specific schema to scope to just
     *                     that schema (SchemaRelationshipsResource) -- this value is
     *                     used as-is, with no visibility check of its own, matching
     *                     SchemaRelationshipsResource's current behavior
     */
    static Graph build(String connectionId, ConnectionContext context, String schemaName) throws Exception {
        List<Relationship> relationships = new ArrayList<>();
        Set<String> allTables = new LinkedHashSet<>();
        Map<String, Set<String>> dependencyGraph = new HashMap<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            for (String schema : resolveSchemas(context, metaData, schemaName)) {
                List<String> tables = new ArrayList<>();
                try (ResultSet rs = metaData.getTables(null, schema, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        tables.add(tableName);
                        String qualifiedName = schema + "." + tableName;
                        allTables.add(qualifiedName);
                        dependencyGraph.putIfAbsent(qualifiedName, new HashSet<>());
                    }
                }

                for (String table : tables) {
                    walkImportedKeys(metaData, connectionId, schema, table, schemaName,
                            relationships, dependencyGraph);
                    if (schemaName != null) {
                        walkExportedKeys(metaData, connectionId, schema, table, schemaName, relationships);
                    }
                }
            }
        }

        TopologicalSortResult sortResult = TopologicalSort.sort(allTables, dependencyGraph);
        return new Graph(relationships, sortResult.sortedTables(), sortResult.cycles());
    }

    /**
     * Schemas to enumerate: just {@code schemaName} if given (no visibility check,
     * matching SchemaRelationshipsResource's current behavior), otherwise every
     * schema {@code context.isSchemaVisible} allows (matching RelationshipsResource).
     */
    private static List<String> resolveSchemas(ConnectionContext context, DatabaseMetaData metaData,
                                                 String schemaName) throws SQLException {
        if (schemaName != null) {
            return List.of(schemaName);
        }
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = metaData.getSchemas()) {
            while (rs.next()) {
                String candidate = rs.getString("TABLE_SCHEM");
                if (context.isSchemaVisible(candidate)) {
                    schemas.add(candidate);
                }
            }
        }
        return schemas;
    }

    /**
     * Walks {@code table}'s imported keys (FKs FROM this table TO another),
     * grouping rows by {@code FK_NAME}. Always records a {@link Relationship} per
     * group; only adds the dependency edge to {@code dependencyGraph} when
     * {@code schemaNameFilter} is null (all-schemas mode) or equal to the
     * referenced table's schema (single-schema mode, intra-schema only).
     */
    private static void walkImportedKeys(DatabaseMetaData metaData, String connectionId,
                                          String schema, String table, String schemaNameFilter,
                                          List<Relationship> relationships,
                                          Map<String, Set<String>> dependencyGraph) throws SQLException {
        LinkedHashMap<String, List<FkRow>> groups = new LinkedHashMap<>();
        try (ResultSet fkRs = metaData.getImportedKeys(null, schema, table)) {
            while (fkRs.next()) {
                String fkName = fkRs.getString("FK_NAME");
                if (fkName == null) continue;
                groups.computeIfAbsent(fkName, k -> new ArrayList<>()).add(new FkRow(
                        fkRs.getString("FKCOLUMN_NAME"), fkRs.getString("PKTABLE_SCHEM"),
                        fkRs.getString("PKTABLE_NAME"), fkRs.getString("PKCOLUMN_NAME")));
            }
        }

        String fromQualified = schema + "." + table;
        for (Map.Entry<String, List<FkRow>> entry : groups.entrySet()) {
            List<FkRow> rows = entry.getValue();
            String pkSchema = rows.get(0).otherSchema();
            String pkTable = rows.get(0).otherTable();

            List<ColumnMapping> mappings = new ArrayList<>();
            for (FkRow row : rows) {
                mappings.add(new ColumnMapping(row.column(), row.otherColumn()));
            }

            relationships.add(new Relationship(
                    entry.getKey(), schema, table, pkSchema, pkTable, mappings,
                    tableUri(connectionId, schema, table),
                    tableUri(connectionId, pkSchema, pkTable)
            ));

            boolean trackDependency = schemaNameFilter == null || schemaNameFilter.equals(pkSchema);
            if (trackDependency) {
                dependencyGraph.computeIfAbsent(fromQualified, k -> new HashSet<>())
                        .add(pkSchema + "." + pkTable);
            }
        }
    }

    /**
     * Walks {@code table}'s exported keys (FKs TO this table FROM another),
     * grouping rows by {@code FK_NAME}. Only called in single-schema mode. Skips
     * groups whose referencing table is also in {@code schemaNameFilter} (already
     * captured by that table's own {@link #walkImportedKeys} call). Never touches
     * the dependency graph -- cross-schema incoming FKs are reported but do not
     * participate in the topological sort, matching current behavior.
     */
    private static void walkExportedKeys(DatabaseMetaData metaData, String connectionId,
                                          String schema, String table, String schemaNameFilter,
                                          List<Relationship> relationships) throws SQLException {
        LinkedHashMap<String, List<FkRow>> groups = new LinkedHashMap<>();
        try (ResultSet fkRs = metaData.getExportedKeys(null, schema, table)) {
            while (fkRs.next()) {
                String fkName = fkRs.getString("FK_NAME");
                if (fkName == null) continue;
                groups.computeIfAbsent(fkName, k -> new ArrayList<>()).add(new FkRow(
                        fkRs.getString("FKCOLUMN_NAME"), fkRs.getString("FKTABLE_SCHEM"),
                        fkRs.getString("FKTABLE_NAME"), fkRs.getString("PKCOLUMN_NAME")));
            }
        }

        for (Map.Entry<String, List<FkRow>> entry : groups.entrySet()) {
            List<FkRow> rows = entry.getValue();
            String fkSchema = rows.get(0).otherSchema();
            String fkTable = rows.get(0).otherTable();

            // The referencing table is also in this schema: already captured via
            // its own walkImportedKeys call above.
            if (schemaNameFilter.equals(fkSchema)) {
                continue;
            }

            List<ColumnMapping> mappings = new ArrayList<>();
            for (FkRow row : rows) {
                // fromTable below is fkTable (row.otherTable()), where row.column()
                // (FKCOLUMN_NAME) lives; toTable is the walked table, where
                // row.otherColumn() (PKCOLUMN_NAME) lives. Matches walkImportedKeys's
                // (fromColumn, toColumn) convention.
                mappings.add(new ColumnMapping(row.column(), row.otherColumn()));
            }

            relationships.add(new Relationship(
                    entry.getKey(), fkSchema, fkTable, schemaNameFilter, table, mappings,
                    tableUri(connectionId, fkSchema, fkTable),
                    tableUri(connectionId, schemaNameFilter, table)
            ));
        }
    }
}
