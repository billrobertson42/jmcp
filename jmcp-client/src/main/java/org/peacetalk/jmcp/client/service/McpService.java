package org.peacetalk.jmcp.client.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.peacetalk.jmcp.client.CommunicationListener;
import org.peacetalk.jmcp.client.McpClient;
import org.peacetalk.jmcp.core.model.CallToolResult;
import org.peacetalk.jmcp.core.model.InitializeResult;
import org.peacetalk.jmcp.core.model.ListResourcesResult;
import org.peacetalk.jmcp.core.model.ListToolsResult;
import org.peacetalk.jmcp.core.model.ReadResourceResult;
import org.peacetalk.jmcp.core.model.ResourceDescriptor;
import org.peacetalk.jmcp.core.model.Tool;

import java.util.List;
import java.util.Map;

/**
 * Service for managing MCP server connections and tool execution.
 * Combines connection lifecycle management with tool operations.
 * Designed to support multiple MCP connections in the future.
 */
public class McpService {
    private static final Logger LOG = LogManager.getLogger(McpService.class);
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
     * List all available resources from the server.
     *
     * @return List of resources result
     * @throws Exception if listing resources fails
     */
    public ListResourcesResult listResources() throws Exception {
        if (client == null) {
            throw new IllegalStateException("Not connected to server");
        }

        return client.listResources();
    }

    /**
     * List resources with pagination cursor.
     *
     * @param cursor Pagination cursor from previous result
     * @return List of resources result
     * @throws Exception if listing resources fails
     */
    public ListResourcesResult listResources(String cursor) throws Exception {
        if (client == null) {
            throw new IllegalStateException("Not connected to server");
        }

        return client.listResources(cursor);
    }

    /**
     * Sort resources alphabetically by name (case-insensitive).
     *
     * @param resources List of resources to sort
     * @return Sorted list of resources
     */
    public List<ResourceDescriptor> sortResources(List<ResourceDescriptor> resources) {
        return resources.stream()
                .sorted((r1, r2) -> r1.name().compareToIgnoreCase(r2.name()))
                .toList();
    }

    /**
     * Read a resource by URI.
     *
     * @param uri The resource URI to read
     * @return Resource content result
     * @throws Exception if reading the resource fails
     */
    public ReadResourceResult readResource(String uri) throws Exception {
        if (client == null) {
            throw new IllegalStateException("Not connected to server");
        }

        return client.readResource(uri);
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
            LOG.warn("Ping failed: {}", e.getMessage());
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

