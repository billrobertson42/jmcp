package org.peacetalk.jmcp.client;

import org.peacetalk.jmcp.core.model.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * High-level MCP client that manages server connection and protocol operations
 */
public class McpClient implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StdioClientTransport transport;
    private InitializeResult serverInfo;

    public McpClient(String[] serverCommand) {
        this.transport = new StdioClientTransport(serverCommand);
    }

    /**
     * Connect to the server and perform initialization
     */
    public void connect() throws IOException {
        transport.connect();

        // Send initialize request
        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", "2024-11-05");
        params.put("capabilities", new ClientCapabilities(null, null));
        params.put("clientInfo", new Implementation("jmcp-client", "1.0.0-SNAPSHOT"));

        JsonRpcResponse response = transport.sendRequest("initialize", params);

        if (response.error() != null) {
            throw new IOException("Initialization failed: " + response.error().message());
        }

        // Parse server info
        serverInfo = MAPPER.convertValue(response.result(), InitializeResult.class);

        // Send initialized notification
        transport.sendRequest("notifications/initialized", new HashMap<>());
    }

    /**
     * List available tools from the server
     */
    public ListToolsResult listTools() throws IOException {
        JsonRpcResponse response = transport.sendRequest("tools/list", new HashMap<>());

        if (response.error() != null) {
            throw new IOException("Failed to list tools: " + response.error().message());
        }

        return MAPPER.convertValue(response.result(), ListToolsResult.class);
    }

    /**
     * Call a tool with the given arguments
     */
    public CallToolResult callTool(String toolName, Map<String, Object> arguments) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);

        JsonRpcResponse response = transport.sendRequest("tools/call", params);

        if (response.error() != null) {
            throw new IOException("Tool call failed: " + response.error().message());
        }

        return MAPPER.convertValue(response.result(), CallToolResult.class);
    }

    /**
     * Get server information from initialization
     */
    public InitializeResult getServerInfo() {
        return serverInfo;
    }

    /**
     * Add a listener for communication events
     */
    public void addCommunicationListener(CommunicationListener listener) {
        transport.addListener(listener);
    }

    /**
     * Remove a listener for communication events
     */
    public void removeCommunicationListener(CommunicationListener listener) {
        transport.removeListener(listener);
    }

    /**
     * Check if connected to server
     */
    public boolean isConnected() {
        return transport.isConnected();
    }

    @Override
    public void close() {
        transport.close();
    }
}

