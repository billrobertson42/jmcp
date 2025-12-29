package org.peacetalk.jmcp.client.service;

import org.peacetalk.jmcp.client.CommunicationListener;
import org.peacetalk.jmcp.client.McpClient;
import org.peacetalk.jmcp.core.model.CallToolResult;
import org.peacetalk.jmcp.core.model.InitializeResult;
import org.peacetalk.jmcp.core.model.ListToolsResult;
import org.peacetalk.jmcp.core.model.Tool;

import java.util.List;
import java.util.Map;

/**
 * Service for managing MCP server connections and tool execution.
 * Combines connection lifecycle management with tool operations.
 * Designed to support multiple MCP connections in the future.
 */
public class McpService {
    private McpClient client;

    /**
     * Connect to an MCP server.
     *
     * @param command Command parts to launch the server
     * @param listener Listener for communication events
     * @throws Exception if connection fails
     */
    public void connect(String[] command, CommunicationListener listener) throws Exception {
        client = new McpClient(command);

        if (listener != null) {
            client.addCommunicationListener(listener);
        }

        client.connect();
    }

    /**
     * Disconnect from the MCP server.
     */
    public void disconnect() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /**
     * Check if currently connected to a server.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /**
     * Get server information from the initialization.
     *
     * @return Server initialization result
     * @throws IllegalStateException if not connected
     */
    public InitializeResult getServerInfo() {
        if (client == null) {
            throw new IllegalStateException("Not connected to server");
        }
        return client.getServerInfo();
    }

    /**
     * List all available tools from the server.
     *
     * @return List of tools
     * @throws Exception if listing tools fails
     */
    public List<Tool> listTools() throws Exception {
        if (client == null) {
            throw new IllegalStateException("Not connected to server");
        }

        ListToolsResult result = client.listTools();
        return result.tools();
    }

    /**
     * Sort tools alphabetically by name (case-insensitive).
     *
     * @param tools List of tools to sort
     * @return Sorted list of tools
     */
    public List<Tool> sortTools(List<Tool> tools) {
        return tools.stream()
                .sorted((t1, t2) -> t1.name().compareToIgnoreCase(t2.name()))
                .toList();
    }

    /**
     * Execute a tool with the given arguments.
     *
     * @param toolName Name of the tool to execute
     * @param arguments Map of argument names to values
     * @return Result of tool execution
     * @throws Exception if execution fails
     */
    public CallToolResult executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        if (client == null) {
            throw new IllegalStateException("Not connected to server");
        }

        return client.callTool(toolName, arguments);
    }

    /**
     * Ping the server to check if it's still responsive.
     *
     * @return true if server responds successfully, false otherwise
     */
    public boolean ping() {
        if (client == null) {
            return false;
        }

        try {
            return client.ping();
        } catch (Exception e) {
            System.err.println("Ping failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cleanup resources when shutting down.
     */
    public void cleanup() {
        disconnect();
    }
}

