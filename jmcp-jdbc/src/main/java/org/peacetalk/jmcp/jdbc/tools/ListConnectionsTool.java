package org.peacetalk.jmcp.jdbc.tools;

import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;
import org.peacetalk.jmcp.jdbc.tools.results.ListConnectionsResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Tool for listing available database connections
 */
public class ListConnectionsTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ConnectionManager connectionManager;

    public ListConnectionsTool(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public String getName() {
        return "list-connections";
    }

    @Override
    public String getDescription() {
        return "List all available database connections with their IDs, URLs, usernames, and database types. Shows which connection is the default.";
    }

    @Override
    public JsonNode getInputSchema() {
        // No input parameters needed
        ObjectSchema schema = new ObjectSchema(Map.of(), List.of());
        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params) throws Exception {
        List<ConnectionInfo> connections = connectionManager.listConnections();
        String defaultId = connectionManager.getDefaultConnectionId();

        return new ListConnectionsResult(connections, defaultId);
    }
}

