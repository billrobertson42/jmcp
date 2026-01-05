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
 * Resource providing foreign key relationships for a specific schema.
 *
 * URI: db://connection/{id}/schema/{schema}/relationships
 *
 * This resource provides all foreign key relationships where at least one
 * of the tables (source or target) is in the specified schema. This is useful
 * for projects that work within a single schema and want to understand
 * dependencies without the noise of unrelated schemas.
 *
 * Includes:
 * - Relationships between tables within the schema
 * - Relationships from tables in this schema to tables in other schemas
 * - Relationships from tables in other schemas to tables in this schema
 */
public class SchemaRelationshipsResource implements Resource {
    private final String connectionId;
    private final String schemaName;
    private final ConnectionManager connectionManager;

    public SchemaRelationshipsResource(String connectionId, String schemaName,
                                      ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.schemaName = schemaName;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return schemaRelationshipsUri(connectionId, schemaName);
    }

    @Override
    public String getName() {
        return "Relationships for schema: " + schemaName;
    }

    @Override
    public String getDescription() {
        return "Foreign key relationships involving this schema (including cross-schema FKs).";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);
        List<Relationship> relationships = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get all tables in this schema
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(null, schemaName, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }

            // For each table in this schema, get its imported keys (FK FROM this table)
            for (String table : tables) {
                try (ResultSet fkRs = metaData.getImportedKeys(null, schemaName, table)) {
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

                        if (!fkName.equals(currentFkName)) {
                            if (currentFkName != null) {
                                relationships.add(new Relationship(
                                    currentFkName,
                                    schemaName,
                                    table,
                                    currentPkSchema,
                                    currentPkTable,
                                    new ArrayList<>(currentMappings),
                                    tableUri(connectionId, schemaName, table),
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
                            schemaName,
                            table,
                            currentPkSchema,
                            currentPkTable,
                            currentMappings,
                            tableUri(connectionId, schemaName, table),
                            tableUri(connectionId, currentPkSchema, currentPkTable)
                        ));
                    }
                }

                // Also get exported keys (FK TO this table from other schemas)
                // This is important to see cross-schema dependencies
                try (ResultSet fkRs = metaData.getExportedKeys(null, schemaName, table)) {
                    String currentFkName = null;
                    List<ColumnMapping> currentMappings = new ArrayList<>();
                    String currentFkSchema = null;
                    String currentFkTable = null;

                    while (fkRs.next()) {
                        String fkName = fkRs.getString("FK_NAME");
                        String fkColumn = fkRs.getString("FKCOLUMN_NAME");
                        String fkSchema = fkRs.getString("FKTABLE_SCHEM");
                        String fkTable = fkRs.getString("FKTABLE_NAME");
                        String pkColumn = fkRs.getString("PKCOLUMN_NAME");

                        if (fkName == null) continue;

                        // Skip if the referencing table is also in this schema
                        // (we already captured it in the imported keys loop)
                        if (schemaName.equals(fkSchema)) continue;

                        if (!fkName.equals(currentFkName)) {
                            if (currentFkName != null) {
                                relationships.add(new Relationship(
                                    currentFkName,
                                    currentFkSchema,
                                    currentFkTable,
                                    schemaName,
                                    table,
                                    new ArrayList<>(currentMappings),
                                    tableUri(connectionId, currentFkSchema, currentFkTable),
                                    tableUri(connectionId, schemaName, table)
                                ));
                            }
                            currentFkName = fkName;
                            currentMappings.clear();
                            currentFkSchema = fkSchema;
                            currentFkTable = fkTable;
                        }
                        currentMappings.add(new ColumnMapping(pkColumn, fkColumn));
                    }

                    if (currentFkName != null) {
                        relationships.add(new Relationship(
                            currentFkName,
                            currentFkSchema,
                            currentFkTable,
                            schemaName,
                            table,
                            currentMappings,
                            tableUri(connectionId, currentFkSchema, currentFkTable),
                            tableUri(connectionId, schemaName, table)
                        ));
                    }
                }
            }
        }

        SchemaRelationshipsResponse response = new SchemaRelationshipsResponse(
            connectionId,
            schemaName,
            relationships,
            new NavigationLinks(
                schemaUri(connectionId, schemaName)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record SchemaRelationshipsResponse(
        String connectionId,
        String schema,
        List<Relationship> relationships,
        NavigationLinks links
    ) {}

    /**
     * A single foreign key relationship
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

