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
 * Resource representing the list of schemas in a database connection.
 *
 * URI: db://connection/{id}/schemas
 *
 * Returns a navigable list of schemas with links to each schema resource.
 */
public class SchemasListResource implements Resource {
    private final String connectionId;
    private final ConnectionManager connectionManager;

    public SchemasListResource(String connectionId, ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return connectionSchemasUri(connectionId);
    }

    @Override
    public String getName() {
        return "Schemas in " + connectionId;
    }

    @Override
    public String getDescription() {
        return "List of database schemas/catalogs. Navigate to individual schemas for table listings.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        List<SchemaEntry> schemas = new ArrayList<>();

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String defaultSchema = conn.getSchema();

            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    String catalogName = rs.getString("TABLE_CATALOG");
                    boolean isDefault = schemaName != null && schemaName.equals(defaultSchema);

                    schemas.add(new SchemaEntry(
                        schemaName,
                        catalogName,
                        isDefault,
                        schemaUri(connectionId, schemaName),
                        schemaTablesUri(connectionId, schemaName)
                    ));
                }
            }
        }

        SchemasResponse response = new SchemasResponse(
            schemas,
            schemas.size(),
            new NavigationLinks(
                connectionUri(connectionId)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record SchemasResponse(
        List<SchemaEntry> schemas,
        int count,
        NavigationLinks links
    ) {}

    /**
     * Schema entry with navigation links
     */
    public record SchemaEntry(
        String name,
        String catalog,
        boolean isDefault,
        String resourceUri,
        String tablesUri
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent
    ) {}
}
