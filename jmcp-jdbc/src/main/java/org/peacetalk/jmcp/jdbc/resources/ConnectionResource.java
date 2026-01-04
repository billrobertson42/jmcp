package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.peacetalk.jmcp.jdbc.resources.Util.MAPPER;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionsUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionSchemasUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.relationshipsUri;

/**
 * Resource representing a specific database connection.
 *
 * URI: db://connection/{id}
 *
 * Returns connection details with navigation links to schemas and other sub-resources.
 */
public class ConnectionResource implements Resource {
    private final String connectionId;
    private final ConnectionManager connectionManager;

    public ConnectionResource(String connectionId, ConnectionManager connectionManager) {
        this.connectionId = connectionId;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return connectionUri(connectionId);
    }

    @Override
    public String getName() {
        return "Connection: " + connectionId;
    }

    @Override
    public String getDescription() {
        return "Database connection details with navigation to schemas, tables, and views.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        ConnectionInfo info = connectionManager.listConnections().stream()
            .filter(c -> c.id().equals(connectionId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));

        ConnectionContext context = connectionManager.getContext(connectionId);
        DatabaseInfo dbInfo;

        try (Connection conn = context.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            dbInfo = new DatabaseInfo(
                metaData.getDatabaseProductName(),
                metaData.getDatabaseProductVersion(),
                metaData.getDriverName(),
                metaData.getDriverVersion(),
                conn.getCatalog(),
                conn.getSchema()
            );
        }

        ConnectionResponse response = new ConnectionResponse(
            info.id(),
            info.databaseType(),
            info.url(),
            info.username(),
            dbInfo,
            new NavigationLinks(
                connectionsUri(),
                connectionSchemasUri(connectionId),
                relationshipsUri(connectionId)
            )
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record ConnectionResponse(
        String id,
        String databaseType,
        String url,
        String username,
        DatabaseInfo database,
        NavigationLinks links
    ) {}

    /**
     * Database metadata information
     */
    public record DatabaseInfo(
        String productName,
        String productVersion,
        String driverName,
        String driverVersion,
        String currentCatalog,
        String currentSchema
    ) {}

    /**
     * Navigation links for HATEOAS-style navigation
     */
    public record NavigationLinks(
        String parent,
        String schemas,
        String relationships
    ) {}
}

