package org.peacetalk.jmcp.jdbc;


import tools.jackson.databind.JsonNode;

/**
 * Definition of a JDBC tool for MCP
 */
public interface JdbcTool {
    /**
     * Get the tool name
     */
    String getName();

    /**
     * Get the tool description
     */
    String getDescription();

    /**
     * Get the JSON schema for tool input parameters
     */
    JsonNode getInputSchema();

    /**
     * Execute the tool with given parameters and connection
     */
    Object execute(JsonNode params, ConnectionContext context) throws Exception;
}

