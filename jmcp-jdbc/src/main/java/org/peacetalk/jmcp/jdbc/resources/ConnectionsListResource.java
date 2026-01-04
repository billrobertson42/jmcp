package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.JdbcUrlSanitizer;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.util.ArrayList;
import java.util.List;

import static org.peacetalk.jmcp.jdbc.resources.Util.MAPPER;
import static org.peacetalk.jmcp.jdbc.resources.Util.SCHEME;

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
        return SCHEME + "://connections";
    }

    @Override
    public String getName() {
        return "Database Connections";
    }

    @Override
    public String getDescription() {
        return "List of available database connections. Navigate to individual connections to explore schemas and tables.";
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
                SCHEME + "://connection/" + info.id(),
                SCHEME + "://connection/" + info.id() + "/schemas"
            ));
        }

        ConnectionsResponse response = new ConnectionsResponse(
            connections,
            connections.size()
        );

        return MAPPER.writeValueAsString(response);
    }

    /**
     * Response record for serialization
     */
    public record ConnectionsResponse(
        List<ConnectionEntry> connections,
        int count
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

