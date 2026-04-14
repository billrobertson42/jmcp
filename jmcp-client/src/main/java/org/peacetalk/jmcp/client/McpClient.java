/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

        // Send initialized notification (no response expected)
        transport.sendNotification("notifications/initialized", new HashMap<>());
    }

    /**
     * Ping the server to check if it's responsive.
     * Returns true if the server responds successfully.
     */
    public boolean ping() throws IOException {
        JsonRpcResponse response = transport.sendRequest("ping", new HashMap<>());

        // Ping should return empty object {}
        return response.error() == null;
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
     * List available resources from the server
     */
    public ListResourcesResult listResources() throws IOException {
        return listResources(null);
    }

    /**
     * List available resources from the server with pagination
     * @param cursor Optional pagination cursor
     */
    public ListResourcesResult listResources(String cursor) throws IOException {
        Map<String, Object> params = new HashMap<>();
        if (cursor != null) {
            params.put("cursor", cursor);
        }

        JsonRpcResponse response = transport.sendRequest("resources/list", params);

        if (response.error() != null) {
            throw new IOException("Failed to list resources: " + response.error().message());
        }

        return MAPPER.convertValue(response.result(), ListResourcesResult.class);
    }

    /**
     * Read a resource by URI
     * @param uri The resource URI to read
     */
    public ReadResourceResult readResource(String uri) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("uri", uri);

        JsonRpcResponse response = transport.sendRequest("resources/read", params);

        if (response.error() != null) {
            throw new IOException("Failed to read resource: " + response.error().message());
        }

        return MAPPER.convertValue(response.result(), ReadResourceResult.class);
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
