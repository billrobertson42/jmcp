package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.JdbcUrlSanitizer;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.util.ArrayList;
import java.util.List;

import static org.peacetalk.jmcp.jdbc.resources.Util.MAPPER;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionsUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionSchemasUri;

/**
 * Resource representing the list of all database connections.
 *
 * URI: db://connections
 *
 * Returns a navigable list of connections with links to each connection resource.
 */
public class ConnectionsListResource implements Resource {
    private final ConnectionManager connectionManager;

    public ConnectionsListResource(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public String getUri() {
        return connectionsUri();
    }

    @Override
    public String getName() {
        return "Database Connections";
    }

    @Override
    public String getDescription() {
        return "List of all configured database connections with navigation links.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public String read() throws Exception {
        List<ConnectionEntry> connections = new ArrayList<>();

        for (ConnectionInfo info : connectionManager.listConnections()) {
            connections.add(new ConnectionEntry(
                info.id(),
                info.databaseType(),
                JdbcUrlSanitizer.getExposableUrl(info.url(), connectionManager.isExposeUrls()),
                info.username(),
                connectionUri(info.id()),
                connectionSchemasUri(info.id())
            ));
        }

        ConnectionsResponse response = new ConnectionsResponse(
            connections
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record ConnectionsResponse(
        List<ConnectionEntry> connections
    ) {}

    /**
     * Connection entry with navigation links
     */
    public record ConnectionEntry(
        String id,
        String databaseType,
        String url,
        String username,
        String resourceUri,
        String schemasUri
    ) {
    }
}

