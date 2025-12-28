package org.peacetalk.jmcp.jdbc;

import org.peacetalk.jmcp.core.Tool;
import tools.jackson.databind.JsonNode;

/**
 * Adapter that wraps a JdbcTool to implement the core Tool interface.
 * This adapter handles the connection context resolution internally.
 */
public class JdbcToolAdapter implements Tool {
    private final JdbcTool jdbcTool;
    private final ConnectionContextResolver contextResolver;

    public JdbcToolAdapter(JdbcTool jdbcTool, ConnectionContextResolver contextResolver) {
        this.jdbcTool = jdbcTool;
        this.contextResolver = contextResolver;
    }

    @Override
    public String getName() {
        return jdbcTool.getName();
    }

    @Override
    public String getDescription() {
        return jdbcTool.getDescription();
    }

    @Override
    public JsonNode getInputSchema() {
        return jdbcTool.getInputSchema();
    }

    @Override
    public Object execute(JsonNode params) throws Exception {
        // Resolve connection context from params or use default
        String connectionId = params.has("database_id") ?
            params.get("database_id").asString() : contextResolver.getDefaultConnectionId();

        ConnectionContext context = contextResolver.getConnectionContext(connectionId);
        if (context == null) {
            throw new IllegalArgumentException("Connection not found: " + connectionId);
        }

        return jdbcTool.execute(params, context);
    }
}

