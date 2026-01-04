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
        return "Complete foreign key relationship graph across all schemas in the database.";
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
                        tables.add(rs.getString("TABLE_NAME"));
                    }

                    // For each table, get its foreign keys
                    for (String table : tables) {
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

        RelationshipsResponse response = new RelationshipsResponse(
            connectionId,
            relationships,
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

