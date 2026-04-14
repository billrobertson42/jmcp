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
import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.model.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MCP protocol handler for resources provided by ResourceProviders.
 * This handler aggregates resources from multiple providers and handles
 * resources/list and resources/read requests.
 *
 * Uses the URI scheme to route requests to the appropriate provider.
 */
public class ResourcesHandler implements McpProtocolHandler {
    private static final Logger LOG = LogManager.getLogger(ResourcesHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ResourceProvider> resourceProviders;

    public ResourcesHandler() {
        this.resourceProviders = new ArrayList<>();
    }

    /**
     * Register a resource provider with this handler.
     * All resources from the provider will be available through this handler.
     *
     * @param provider The resource provider to register
     */
    public void registerResourceProvider(ResourceProvider provider) {
        resourceProviders.add(provider);
    }

    @Override
    public Set<String> getSupportedMethods() {
        return Set.of("resources/list", "resources/read");
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        try {
            return switch (request.method()) {
                case "resources/list" -> handleListResources(request);
                case "resources/read" -> handleReadResource(request);
                default -> JsonRpcResponse.error(request.id(), JsonRpcError.methodNotFound(request.method()));
            };
        } catch (Exception e) {
            LOG.error("Error handling request: {}", request.method(), e);
            return JsonRpcResponse.error(request.id(), JsonRpcError.internalError(e.getMessage()));
        }
    }

    private JsonRpcResponse handleListResources(JsonRpcRequest request) {
        // Extract cursor from params if present
        String cursor = null;
        if (request.params() != null) {
            JsonNode paramsNode = MAPPER.valueToTree(request.params());
            if (paramsNode.has("cursor")) {
                cursor = paramsNode.get("cursor").asString();
            }
        }

        List<ResourceDescriptor> resourceList = new ArrayList<>();

        // Aggregate resources from all registered providers
        for (ResourceProvider provider : resourceProviders) {
            for (Resource resource : provider.listResources(cursor)) {
                resourceList.add(new ResourceDescriptor(
                    resource.getUri(),
                    resource.getName(),
                    resource.getDescription(),
                    resource.getMimeType()
                ));
            }
        }

        ListResourcesResult result = ListResourcesResult.of(resourceList);
        return JsonRpcResponse.success(request.id(), result);
    }

    private JsonRpcResponse handleReadResource(JsonRpcRequest request) {
        try {
            // Parse the read resource request
            ReadResourceRequest readRequest = MAPPER.convertValue(request.params(), ReadResourceRequest.class);
            String uri = readRequest.uri();

            // Extract scheme from URI
            String scheme = extractScheme(uri);
            if (scheme == null) {
                return JsonRpcResponse.error(request.id(),
                    JsonRpcError.invalidParams("Invalid resource URI: " + uri));
            }

            // Find the provider that handles this scheme
            ResourceProvider targetProvider = null;
            for (ResourceProvider provider : resourceProviders) {
                if (provider.supportsScheme(scheme)) {
                    targetProvider = provider;
                    break;
                }
            }

            if (targetProvider == null) {
                return JsonRpcResponse.error(request.id(),
                    JsonRpcError.invalidParams("No provider found for URI scheme: " + scheme));
            }

            // Get the resource
            Resource resource = targetProvider.getResource(uri);
            if (resource == null) {
                return JsonRpcResponse.error(request.id(),
                    JsonRpcError.invalidParams("Resource not found: " + uri));
            }

            // Read the resource content
            String content = resource.read();

            // Build result
            ReadResourceResult result = ReadResourceResult.text(
                resource.getUri(),
                resource.getMimeType(),
                content
            );

            return JsonRpcResponse.success(request.id(), result);

        } catch (IllegalArgumentException e) {
            return JsonRpcResponse.error(request.id(),
                JsonRpcError.invalidParams(e.getMessage()));
        } catch (Exception e) {
            LOG.error("Resource read failed: " + e.getMessage(), e);
            return JsonRpcResponse.error(request.id(),
                JsonRpcError.internalError("Resource read failed: " + e.getMessage()));
        }
    }

    /**
     * Extract the scheme from a URI.
     *
     * @param uri The URI (e.g., "db://connections")
     * @return The scheme (e.g., "jdbc"), or null if invalid
     */
    private String extractScheme(String uri) {
        if (uri == null) {
            return null;
        }
        int colonIndex = uri.indexOf("://");
        if (colonIndex <= 0) {
            return null;
        }
        return uri.substring(0, colonIndex);
    }
}

