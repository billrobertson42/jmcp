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

package org.peacetalk.jmcp.core.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.peacetalk.jmcp.core.model.*;

import java.util.Map;
import java.util.Set;

/**
 * Handler for MCP initialization and lifecycle protocol methods.
 *
 * Handles:
 * - initialize: Initial handshake, returns server capabilities
 * - notifications/initialized: Client signals it's ready (no response)
 * - ping: Health check
 */
public class InitializationHandler implements McpProtocolHandler {
    private static final Logger LOG = LogManager.getLogger(InitializationHandler.class);
    private static final String VERSION = "1.0.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final boolean hasTools;
    private final boolean hasResources;
    private boolean initialized = false;

    /**
     * Create a handler that advertises dynamic capabilities based on what
     * providers were actually assembled.
     *
     * @param hasTools     true if at least one tool provider was registered
     * @param hasResources true if at least one resource provider was registered
     */
    public InitializationHandler(boolean hasTools, boolean hasResources) {
        this.hasTools = hasTools;
        this.hasResources = hasResources;
    }

    /**
     * Backward-compatible default: advertise both tools and resources capabilities.
     */
    public InitializationHandler() {
        this(true, true);
    }

    @Override
    public Set<String> getSupportedMethods() {
        return Set.of("initialize", "notifications/initialized", "ping");
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        return switch (request.method()) {
            case "initialize" -> handleInitialize(request);
            case "notifications/initialized" -> handleInitialized(request);
            case "ping" -> handlePing(request);
            default -> JsonRpcResponse.error(request.id(), JsonRpcError.methodNotFound(request.method()));
        };
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        // Build server capabilities based on what was actually assembled
        ServerCapabilities capabilities = new ServerCapabilities(
            null, // experimental
            null, // logging
            null, // prompts
            hasResources ? new ServerCapabilities.ResourcesCapability(false, false) : null,
            hasTools ? new ServerCapabilities.ToolsCapability(false) : null
        );

        // Build server info
        Implementation serverInfo = new Implementation("jmcp", VERSION);

        // Build initialize result
        InitializeResult result = new InitializeResult(
            PROTOCOL_VERSION,
            capabilities,
            serverInfo
        );

        return JsonRpcResponse.success(request.id(), result);
    }

    /**
     * Handle the initialized notification from the client.
     * This signals that the client is ready to receive requests.
     * No response is sent for notifications.
     */
    private synchronized JsonRpcResponse handleInitialized(JsonRpcRequest request) {
        initialized = true;
        LOG.info("Client initialized notification received");
        // Notifications don't get responses, but we return null which McpServer handles
        return null;
    }

    /**
     * Handle ping request for health checks.
     * Returns an empty object per MCP spec.
     */
    private JsonRpcResponse handlePing(JsonRpcRequest request) {
        return JsonRpcResponse.success(request.id(), Map.of());
    }

    /**
     * Check if the client has sent the initialized notification.
     */
    public synchronized boolean isInitialized() {
        return initialized;
    }
}
