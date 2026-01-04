package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

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
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName;
    }

    @Override
    public String getName() {
        return "Schema: " + schemaName;
    }

    @Override
    public String getDescription() {
        return "Schema details with navigation to tables, views, and other database objects.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionContext context = connectionManager.getContext(connectionId);

        int tableCount = 0;
        int viewCount = 0;

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Count tables
            try (ResultSet rs = metaData.getTables(null, schemaName, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tableCount++;
                }
            }

            // Count views
            try (ResultSet rs = metaData.getTables(null, schemaName, "%", new String[]{"VIEW"})) {
                while (rs.next()) {
                    viewCount++;
                }
            }
        }

        SchemaResponse response = new SchemaResponse(
            schemaName,
            connectionId,
            tableCount,
            viewCount,
            new NavigationLinks(
                SCHEME + "://connection/" + connectionId + "/schemas",
                SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/tables",
                SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/views"
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
        int tableCount,
        int viewCount,
        NavigationLinks links
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent,
        String tables,
        String views
    ) {}
}

