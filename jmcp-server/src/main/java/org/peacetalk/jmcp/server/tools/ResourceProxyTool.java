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

package org.peacetalk.jmcp.server.tools;

import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;
import org.peacetalk.jmcp.core.schema.ObjectSchema;
import org.peacetalk.jmcp.core.schema.StringProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * A tool that provides access to MCP resources for clients that don't implement
 * the resources protocol (e.g., GitHub Copilot).
 *
 * This tool acts as a bridge, delegating to the ResourcesHandler to expose
 * resource functionality through the tools API.
 */
public class ResourceProxyTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ResourcesHandler resourcesHandler;

    public ResourceProxyTool(ResourcesHandler resourcesHandler) {
        this.resourcesHandler = resourcesHandler;
    }

    @Override
    public String getName() {
        return "resource-proxy";
    }

    @Override
    public String getDescription() {
        return "[Workaround for clients without resource support - ignore if your client supports MCP resources] " +
               "Provides access to MCP resources via the tools API. " +
               "Use operation='list' to see available resources, or operation='read' with a uri to read a resource. " +
               "Start with uri='db://context' for a complete overview.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectSchema schema = new ObjectSchema(
            Map.of(
                "operation", new StringProperty(
                    "Operation: 'list' to list available resources, 'read' to read a resource"
                ),
                "uri", new StringProperty(
                    "Resource URI to read (required when operation='read'). " +
                    "Start with 'db://context' for a complete overview."
                )
            ),
            List.of("operation")
        );

        return MAPPER.valueToTree(schema);
    }

    @Override
    public Object execute(JsonNode params) throws Exception {
        String operation = params.get("operation").asString().toLowerCase();

        return switch (operation) {
            case "list" -> listResources();
            case "read" -> readResource(params);
            default -> throw new IllegalArgumentException(
                "Unknown operation: " + operation + ". Use 'list' or 'read'."
            );
        };
    }

    private Object listResources() {
        // Create a synthetic JSON-RPC request for resources/list
        ObjectNode emptyParams = MAPPER.createObjectNode();
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/list", emptyParams);

        // Delegate to ResourcesHandler
        JsonRpcResponse response = resourcesHandler.handle(request);

        if (response.error() != null) {
            throw new RuntimeException("Failed to list resources: " + response.error().message());
        }

        // Return the result directly - it's already a ListResourcesResult
        return response.result();
    }

    private Object readResource(JsonNode params) throws Exception {
        if (!params.has("uri")) {
            throw new IllegalArgumentException("uri is required when operation='read'");
        }

        // Create a synthetic JSON-RPC request for resources/read
        ObjectNode readParams = MAPPER.createObjectNode();
        readParams.put("uri", params.get("uri").asString());
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/read", readParams);

        // Delegate to ResourcesHandler
        JsonRpcResponse response = resourcesHandler.handle(request);

        if (response.error() != null) {
            throw new RuntimeException("Failed to read resource: " + response.error().message());
        }

        // Return the result directly - it's already a ReadResourceResult
        return response.result();
    }
}




