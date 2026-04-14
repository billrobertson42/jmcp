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

package test.org.peacetalk.jmcp.server.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.ListResourcesResult;
import org.peacetalk.jmcp.core.model.ReadResourceResult;
import org.peacetalk.jmcp.core.model.ResourceContents;
import org.peacetalk.jmcp.core.model.ResourceDescriptor;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;
import org.peacetalk.jmcp.server.tools.ResourceProxyTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ResourceProxyTool, which provides resource access through the tools API.
 */
public class ResourceProxyToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResourcesHandler resourcesHandler;
    private ResourceProxyTool tool;

    @BeforeEach
    public void setUp() {
        resourcesHandler = new ResourcesHandler();
        tool = new ResourceProxyTool(resourcesHandler);
    }

    @Test
    public void testGetName() {
        assertEquals("resource-proxy", tool.getName());
    }

    @Test
    public void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Workaround for clients without resource support"));
        assertTrue(description.contains("ignore if your client supports MCP resources"));
    }

    @Test
    public void testGetInputSchema() {
        JsonNode schema = tool.getInputSchema();
        assertNotNull(schema);

        // Check that operation field is present
        assertTrue(schema.has("properties"));
        JsonNode properties = schema.get("properties");
        assertTrue(properties.has("operation"));
        assertTrue(properties.has("uri"));

        // Check that operation is required
        assertTrue(schema.has("required"));
        JsonNode required = schema.get("required");
        assertTrue(required.isArray());
        boolean foundOperation = false;
        for (JsonNode field : required) {
            if ("operation".equals(field.asString())) {
                foundOperation = true;
                break;
            }
        }
        assertTrue(foundOperation, "operation should be in required fields");
    }

    @Test
    public void testListResourcesWithNoProviders() throws Exception {
        // No providers registered - should return empty list
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "list");

        Object result = tool.execute(params);

        assertNotNull(result);
        assertInstanceOf(ListResourcesResult.class, result);

        ListResourcesResult listResult = (ListResourcesResult) result;
        assertNotNull(listResult.resources());
        assertEquals(0, listResult.resources().size());
    }

    @Test
    public void testListResourcesWithProvider() throws Exception {
        // Create a mock resource provider
        ResourceProvider mockProvider = mock(ResourceProvider.class);
        Resource mockResource1 = mock(Resource.class);
        Resource mockResource2 = mock(Resource.class);

        when(mockProvider.supportsScheme("test")).thenReturn(true);
        when(mockProvider.listResources(null)).thenReturn(List.of(mockResource1, mockResource2));

        when(mockResource1.getUri()).thenReturn("test://resource1");
        when(mockResource1.getName()).thenReturn("Resource 1");
        when(mockResource1.getDescription()).thenReturn("First test resource");
        when(mockResource1.getMimeType()).thenReturn("application/json");

        when(mockResource2.getUri()).thenReturn("test://resource2");
        when(mockResource2.getName()).thenReturn("Resource 2");
        when(mockResource2.getDescription()).thenReturn("Second test resource");
        when(mockResource2.getMimeType()).thenReturn("text/plain");

        resourcesHandler.registerResourceProvider(mockProvider);

        // Execute list operation
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "list");

        Object result = tool.execute(params);

        assertNotNull(result);
        assertInstanceOf(ListResourcesResult.class, result);

        ListResourcesResult listResult = (ListResourcesResult) result;
        List<ResourceDescriptor> resources = listResult.resources();
        assertEquals(2, resources.size());

        // Check first resource
        ResourceDescriptor res1 = resources.get(0);
        assertEquals("test://resource1", res1.uri());
        assertEquals("Resource 1", res1.name());
        assertEquals("First test resource", res1.description());
        assertEquals("application/json", res1.mimeType());

        // Check second resource
        ResourceDescriptor res2 = resources.get(1);
        assertEquals("test://resource2", res2.uri());
        assertEquals("Resource 2", res2.name());
        assertEquals("Second test resource", res2.description());
        assertEquals("text/plain", res2.mimeType());
    }

    @Test
    public void testReadResourceSuccess() throws Exception {
        // Create a mock resource provider
        ResourceProvider mockProvider = mock(ResourceProvider.class);
        Resource mockResource = mock(Resource.class);

        when(mockProvider.supportsScheme("test")).thenReturn(true);
        when(mockProvider.getResource("test://myresource")).thenReturn(mockResource);

        when(mockResource.getUri()).thenReturn("test://myresource");
        when(mockResource.getName()).thenReturn("My Resource");
        when(mockResource.getMimeType()).thenReturn("application/json");
        when(mockResource.read()).thenReturn("{\"data\": \"test\"}");

        resourcesHandler.registerResourceProvider(mockProvider);

        // Execute read operation
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "read");
        params.put("uri", "test://myresource");

        Object result = tool.execute(params);

        assertNotNull(result);
        assertInstanceOf(ReadResourceResult.class, result);

        ReadResourceResult readResult = (ReadResourceResult) result;
        assertNotNull(readResult.contents());
        assertEquals(1, readResult.contents().size());

        ResourceContents content = readResult.contents().get(0);
        assertEquals("test://myresource", content.uri());
        assertEquals("application/json", content.mimeType());

        // Check text content
        assertEquals("{\"data\": \"test\"}", content.text());

        // Verify the resource was read
        verify(mockResource).read();
    }

    @Test
    public void testReadResourceWithoutUri() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "read");
        // No uri provided

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tool.execute(params)
        );

        assertTrue(exception.getMessage().contains("uri is required"));
    }

    @Test
    public void testReadResourceNotFound() throws Exception {
        // Create a mock resource provider that returns null
        ResourceProvider mockProvider = mock(ResourceProvider.class);

        when(mockProvider.supportsScheme("test")).thenReturn(true);
        when(mockProvider.getResource("test://nonexistent")).thenReturn(null);

        resourcesHandler.registerResourceProvider(mockProvider);

        // Execute read operation
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "read");
        params.put("uri", "test://nonexistent");

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tool.execute(params)
        );

        assertTrue(exception.getMessage().contains("Failed to read resource"));
    }

    @Test
    public void testReadResourceInvalidUri() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "read");
        params.put("uri", "invalid-uri-no-scheme");

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tool.execute(params)
        );

        assertTrue(exception.getMessage().contains("Failed to read resource"));
    }

    @Test
    public void testReadResourceNoProviderForScheme() {
        // No provider registered for "unknown" scheme
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "read");
        params.put("uri", "unknown://resource");

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tool.execute(params)
        );

        assertTrue(exception.getMessage().contains("Failed to read resource"));
    }

    @Test
    public void testInvalidOperation() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "delete"); // Invalid operation

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tool.execute(params)
        );

        assertTrue(exception.getMessage().contains("Unknown operation"));
        assertTrue(exception.getMessage().contains("delete"));
    }

    @Test
    public void testMissingOperation() {
        ObjectNode params = MAPPER.createObjectNode();
        // No operation provided

        assertThrows(
            Exception.class,
            () -> tool.execute(params)
        );
    }

    @Test
    public void testOperationCaseInsensitive() throws Exception {
        // Test that operation is case-insensitive
        ObjectNode params1 = MAPPER.createObjectNode();
        params1.put("operation", "LIST");

        Object result1 = tool.execute(params1);
        assertNotNull(result1);
        assertInstanceOf(ListResourcesResult.class, result1);

        ObjectNode params2 = MAPPER.createObjectNode();
        params2.put("operation", "LiSt");

        Object result2 = tool.execute(params2);
        assertNotNull(result2);
        assertInstanceOf(ListResourcesResult.class, result2);
    }

    @Test
    public void testListResourcesWithMultipleProviders() throws Exception {
        // Create two mock resource providers
        ResourceProvider provider1 = mock(ResourceProvider.class);
        ResourceProvider provider2 = mock(ResourceProvider.class);

        Resource resource1 = mock(Resource.class);
        Resource resource2 = mock(Resource.class);

        when(provider1.supportsScheme("test1")).thenReturn(true);
        when(provider1.listResources(null)).thenReturn(List.of(resource1));

        when(provider2.supportsScheme("test2")).thenReturn(true);
        when(provider2.listResources(null)).thenReturn(List.of(resource2));

        when(resource1.getUri()).thenReturn("test1://resource");
        when(resource1.getName()).thenReturn("Provider 1 Resource");
        when(resource1.getDescription()).thenReturn("From provider 1");
        when(resource1.getMimeType()).thenReturn("application/json");

        when(resource2.getUri()).thenReturn("test2://resource");
        when(resource2.getName()).thenReturn("Provider 2 Resource");
        when(resource2.getDescription()).thenReturn("From provider 2");
        when(resource2.getMimeType()).thenReturn("text/plain");

        resourcesHandler.registerResourceProvider(provider1);
        resourcesHandler.registerResourceProvider(provider2);

        // Execute list operation
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "list");

        Object result = tool.execute(params);

        assertNotNull(result);
        assertInstanceOf(ListResourcesResult.class, result);

        ListResourcesResult listResult = (ListResourcesResult) result;
        assertEquals(2, listResult.resources().size());

        // Verify both providers were called
        verify(provider1).listResources(null);
        verify(provider2).listResources(null);
    }

    @Test
    public void testReadResourceWithComplexContent() throws Exception {
        // Test reading a resource with more complex JSON content
        ResourceProvider mockProvider = mock(ResourceProvider.class);
        Resource mockResource = mock(Resource.class);

        String complexJson = """
            {
                "databases": [
                    {"id": "db1", "name": "Database 1"},
                    {"id": "db2", "name": "Database 2"}
                ],
                "count": 2
            }
            """;

        when(mockProvider.supportsScheme("db")).thenReturn(true);
        when(mockProvider.getResource("db://context")).thenReturn(mockResource);

        when(mockResource.getUri()).thenReturn("db://context");
        when(mockResource.getName()).thenReturn("Context");
        when(mockResource.getMimeType()).thenReturn("application/json");
        when(mockResource.read()).thenReturn(complexJson);

        resourcesHandler.registerResourceProvider(mockProvider);

        // Execute read operation
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "read");
        params.put("uri", "db://context");

        Object result = tool.execute(params);

        assertNotNull(result);
        assertInstanceOf(ReadResourceResult.class, result);

        ReadResourceResult readResult = (ReadResourceResult) result;
        ResourceContents content = readResult.contents().get(0);

        // Verify the content is the JSON string
        assertEquals(complexJson, content.text());
    }

    @Test
    public void testDelegationToResourcesHandler() throws Exception {
        // Test that the tool actually delegates to ResourcesHandler
        ResourcesHandler spyHandler = spy(resourcesHandler);
        ResourceProxyTool toolWithSpy = new ResourceProxyTool(spyHandler);

        // Execute list operation
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "list");

        toolWithSpy.execute(params);

        // Verify that handle was called on the ResourcesHandler
        ArgumentCaptor<JsonRpcRequest> requestCaptor = ArgumentCaptor.forClass(JsonRpcRequest.class);
        verify(spyHandler).handle(requestCaptor.capture());

        JsonRpcRequest capturedRequest = requestCaptor.getValue();
        assertEquals("resources/list", capturedRequest.method());
        assertEquals("2.0", capturedRequest.jsonrpc());
    }
}

