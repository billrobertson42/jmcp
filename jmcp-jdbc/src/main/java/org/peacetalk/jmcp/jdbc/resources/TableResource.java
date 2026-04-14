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
 * Resource representing a specific table in a schema.
 *
 * URI: db://connection/{id}/schema/{schema}/table/{table}
 *
 * Returns detailed table information including columns, primary keys, indexes, and foreign keys.
 */
public class TableResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final String tableName;
    private final ConnectionManager connectionManager;

    public TableResource(String connectionId, String schemaName, String tableName,
                         ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return tableUri(connectionId, schemaName, tableName);
    }

    @Override
    public String getName() {
        return "Table: " + tableName;
    }

    @Override
    public String getDescription() {
        return "Columns, PKs, indexes, foreignKeys (tables this references), reverseForeignKeys (tables that reference this).";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        List<ColumnInfo> columns = new ArrayList<>();
        List<String> primaryKey = new ArrayList<>();
        List<IndexInfo> indexes = new ArrayList<>();
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
        List<ReverseForeignKeyInfo> reverseForeignKeys = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get columns
            try (ResultSet rs = metaData.getColumns(null, schemaName, tableName, "%")) {
                while (rs.next()) {
                    columns.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("DECIMAL_DIGITS"),
                        "YES".equals(rs.getString("IS_NULLABLE")),
                        rs.getString("COLUMN_DEF"),
                        "YES".equals(rs.getString("IS_AUTOINCREMENT")),
                        rs.getString("REMARKS")
                    ));
                }
            }

            // Get primary key columns
            try (ResultSet rs = metaData.getPrimaryKeys(null, schemaName, tableName)) {
                while (rs.next()) {
                    primaryKey.add(rs.getString("COLUMN_NAME"));
                }
            }

            // Get indexes
            try (ResultSet rs = metaData.getIndexInfo(null, schemaName, tableName, false, false)) {
                String currentIndexName = null;
                List<String> currentColumns = new ArrayList<>();
                boolean currentUnique = false;

                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) continue;

                    String columnName = rs.getString("COLUMN_NAME");
                    boolean unique = !rs.getBoolean("NON_UNIQUE");

                    if (!indexName.equals(currentIndexName)) {
                        if (currentIndexName != null) {
                            indexes.add(new IndexInfo(currentIndexName, new ArrayList<>(currentColumns), currentUnique));
                        }
                        currentIndexName = indexName;
                        currentColumns.clear();
                        currentUnique = unique;
                    }
                    currentColumns.add(columnName);
                }

                if (currentIndexName != null) {
                    indexes.add(new IndexInfo(currentIndexName, currentColumns, currentUnique));
                }
            }

            // Get foreign keys
            try (ResultSet rs = metaData.getImportedKeys(null, schemaName, tableName)) {
                String currentFkName = null;
                List<ColumnMapping> currentMappings = new ArrayList<>();
                String currentPkTable = null;
                String currentPkSchema = null;
                String currentOnDelete = null;
                String currentOnUpdate = null;

                while (rs.next()) {
                    String fkName = rs.getString("FK_NAME");
                    String fkColumn = rs.getString("FKCOLUMN_NAME");
                    String pkTable = rs.getString("PKTABLE_NAME");
                    String pkSchema = rs.getString("PKTABLE_SCHEM");
                    String pkColumn = rs.getString("PKCOLUMN_NAME");

                    // Get referential actions
                    short deleteRule = rs.getShort("DELETE_RULE");
                    short updateRule = rs.getShort("UPDATE_RULE");
                    String onDelete = getReferentialAction(deleteRule);
                    String onUpdate = getReferentialAction(updateRule);

                    if (fkName == null) continue;

                    if (!fkName.equals(currentFkName)) {
                        if (currentFkName != null) {
                            foreignKeys.add(new ForeignKeyInfo(
                                currentFkName,
                                currentPkSchema,
                                currentPkTable,
                                new ArrayList<>(currentMappings),
                                tableUri(connectionId, currentPkSchema, currentPkTable),
                                currentOnDelete,
                                currentOnUpdate
                            ));
                        }
                        currentFkName = fkName;
                        currentMappings.clear();
                        currentPkTable = pkTable;
                        currentPkSchema = pkSchema;
                        currentOnDelete = onDelete;
                        currentOnUpdate = onUpdate;
                    }
                    currentMappings.add(new ColumnMapping(fkColumn, pkColumn));
                }

                if (currentFkName != null) {
                    foreignKeys.add(new ForeignKeyInfo(
                        currentFkName,
                        currentPkSchema,
                        currentPkTable,
                        currentMappings,
                        tableUri(connectionId, currentPkSchema, currentPkTable),
                        currentOnDelete,
                        currentOnUpdate
                    ));
                }
            }

            // Get exported keys (reverse foreign keys - tables that reference this table)
            try (ResultSet rs = metaData.getExportedKeys(null, schemaName, tableName)) {
                String currentFkName = null;
                List<ColumnMapping> currentMappings = new ArrayList<>();
                String currentFkTable = null;
                String currentFkSchema = null;
                String currentOnDelete = null;
                String currentOnUpdate = null;

                while (rs.next()) {
                    String fkName = rs.getString("FK_NAME");
                    String fkColumn = rs.getString("FKCOLUMN_NAME");
                    String fkTable = rs.getString("FKTABLE_NAME");
                    String fkSchema = rs.getString("FKTABLE_SCHEM");
                    String pkColumn = rs.getString("PKCOLUMN_NAME");

                    // Get referential actions
                    short deleteRule = rs.getShort("DELETE_RULE");
                    short updateRule = rs.getShort("UPDATE_RULE");
                    String onDelete = getReferentialAction(deleteRule);
                    String onUpdate = getReferentialAction(updateRule);

                    if (fkName == null) continue;

                    if (!fkName.equals(currentFkName)) {
                        if (currentFkName != null) {
                            reverseForeignKeys.add(new ReverseForeignKeyInfo(
                                currentFkName,
                                currentFkSchema,
                                currentFkTable,
                                new ArrayList<>(currentMappings),
                                tableUri(connectionId, currentFkSchema, currentFkTable),
                                currentOnDelete,
                                currentOnUpdate
                            ));
                        }
                        currentFkName = fkName;
                        currentMappings.clear();
                        currentFkTable = fkTable;
                        currentFkSchema = fkSchema;
                        currentOnDelete = onDelete;
                        currentOnUpdate = onUpdate;
                    }
                    currentMappings.add(new ColumnMapping(pkColumn, fkColumn));
                }

                if (currentFkName != null) {
                    reverseForeignKeys.add(new ReverseForeignKeyInfo(
                        currentFkName,
                        currentFkSchema,
                        currentFkTable,
                        currentMappings,
                        tableUri(connectionId, currentFkSchema, currentFkTable),
                        currentOnDelete,
                        currentOnUpdate
                    ));
                }
            }
        }

        TableResponse response = new TableResponse(
            tableName,
            schemaName,
            connectionId,
            columns,
            primaryKey,
            indexes,
            foreignKeys,
            reverseForeignKeys,
            new NavigationLinks(
                schemaUri(connectionId, schemaName)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Convert JDBC referential action code to readable string
     */
    private static String getReferentialAction(short actionCode) {
        return switch (actionCode) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            default -> null;
        };
    }

    /**
     * Response record for serialization
     */
    public record TableResponse(
        String name,
        String schema,
        String connectionId,
        List<ColumnInfo> columns,
        List<String> primaryKey,
        List<IndexInfo> indexes,
        List<ForeignKeyInfo> foreignKeys,
        List<ReverseForeignKeyInfo> reverseForeignKeys,
        NavigationLinks links
    ) {}

    /**
     * Column information with extended metadata
     */
    public record ColumnInfo(
        String name,
        String dataType,
        int size,
        int decimalDigits,
        boolean nullable,
        String defaultValue,
        boolean autoIncrement,
        String remarks
    ) {}

    /**
     * Index information
     */
    public record IndexInfo(
        String name,
        List<String> columns,
        boolean unique
    ) {}

    /**
     * Foreign key information with navigation link to referenced table
     */
    public record ForeignKeyInfo(
        String name,
        String referencedSchema,
        String referencedTable,
        List<ColumnMapping> columns,
        String referencedTableUri,
        String onDelete,
        String onUpdate
    ) {}

    /**
     * Reverse foreign key information (tables that reference this table)
     * with navigation link to the referencing table
     */
    public record ReverseForeignKeyInfo(
        String name,
        String referencingSchema,
        String referencingTable,
        List<ColumnMapping> columns,
        String referencingTableUri,
        String onDelete,
        String onUpdate
    ) {}

    /**
     * Column mapping for foreign keys
     */
    public record ColumnMapping(
        String sourceColumn,
        String targetColumn
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}

