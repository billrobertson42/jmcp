package test.org.peacetalk.jmcp.core.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResourcesHandler - MCP protocol handler for resources.
 */
class ResourcesHandlerTest {

    private ResourcesHandler handler;
    private ObjectMapper mapper;
    private TestResourceProvider testProvider;

    @BeforeEach
    void setUp() {
        handler = new ResourcesHandler();
        mapper = new ObjectMapper();
        testProvider = new TestResourceProvider();
        handler.registerResourceProvider(testProvider);
    }

    @Test
    void testGetSupportedMethods() {
        Set<String> methods = handler.getSupportedMethods();
        assertTrue(methods.contains("resources/list"));
        assertTrue(methods.contains("resources/read"));
        assertEquals(2, methods.size());
    }

    @Test
    void testHandleListResources() {
        ObjectNode params = mapper.createObjectNode();
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/list", params);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
        assertNotNull(response.result());

        JsonNode result = mapper.valueToTree(response.result());
        assertTrue(result.has("resources"));
        assertEquals(2, result.get("resources").size());

        // Check first resource
        JsonNode firstResource = result.get("resources").get(0);
        assertEquals("test://root", firstResource.get("uri").asString());
        assertEquals("Test Root", firstResource.get("name").asString());
    }

    @Test
    void testHandleListResourcesWithCursor() {
        ObjectNode params = mapper.createObjectNode();
        params.put("cursor", "somecursor");
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/list", params);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
    }

    @Test
    void testHandleReadResource() {
        ObjectNode params = mapper.createObjectNode();
        params.put("uri", "test://root");
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/read", params);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
        assertNotNull(response.result());

        JsonNode result = mapper.valueToTree(response.result());
        assertTrue(result.has("contents"));
        assertEquals(1, result.get("contents").size());

        JsonNode content = result.get("contents").get(0);
        assertEquals("test://root", content.get("uri").asString());
        assertEquals("application/json", content.get("mimeType").asString());
        assertEquals("{\"data\":\"root\"}", content.get("text").asString());
    }

    @Test
    void testHandleReadResourceNotFound() {
        ObjectNode params = mapper.createObjectNode();
        params.put("uri", "test://nonexistent");
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/read", params);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("Resource not found"));
    }

    @Test
    void testHandleReadResourceInvalidScheme() {
        ObjectNode params = mapper.createObjectNode();
        params.put("uri", "unknown://something");
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/read", params);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("No provider found"));
    }

    @Test
    void testHandleReadResourceInvalidUri() {
        ObjectNode params = mapper.createObjectNode();
        params.put("uri", "invaliduri");
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/read", params);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("Invalid resource URI"));
    }

    @Test
    void testHandleMethodNotFound() {
        ObjectNode params = mapper.createObjectNode();
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/invalid", params);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNotNull(response.error());
        assertEquals(-32601, response.error().code());
    }

    @Test
    void testMultipleProviders() {
        // Add another provider
        ResourcesHandler multiHandler = new ResourcesHandler();
        multiHandler.registerResourceProvider(testProvider);
        multiHandler.registerResourceProvider(new AnotherResourceProvider());

        ObjectNode params = mapper.createObjectNode();
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/list", params);

        JsonRpcResponse response = multiHandler.handle(request);

        assertNotNull(response);
        assertNull(response.error());

        JsonNode result = mapper.valueToTree(response.result());
        // Should have resources from both providers
        assertTrue(result.get("resources").size() > 2);
    }

    @Test
    void testReadFromSecondProvider() {
        ResourcesHandler multiHandler = new ResourcesHandler();
        multiHandler.registerResourceProvider(testProvider);
        multiHandler.registerResourceProvider(new AnotherResourceProvider());

        ObjectNode params = mapper.createObjectNode();
        params.put("uri", "other://data");
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/read", params);

        JsonRpcResponse response = multiHandler.handle(request);

        assertNotNull(response);
        assertNull(response.error());
    }

    /**
     * Test implementation of ResourceProvider
     */
    static class TestResourceProvider implements ResourceProvider {
        @Override
        public void initialize() {}

        @Override
        public List<Resource> listResources(String cursor) {
            return List.of(
                new TestResource("test://root", "Test Root", "{\"data\":\"root\"}"),
                new TestResource("test://child", "Test Child", "{\"data\":\"child\"}")
            );
        }

        @Override
        public Resource getResource(String uri) {
            if ("test://root".equals(uri)) {
                return new TestResource("test://root", "Test Root", "{\"data\":\"root\"}");
            } else if ("test://child".equals(uri)) {
                return new TestResource("test://child", "Test Child", "{\"data\":\"child\"}");
            }
            return null;
        }

        @Override
        public boolean supportsScheme(String scheme) {
            return "test".equals(scheme);
        }

        @Override
        public void shutdown() {}

        @Override
        public String getName() {
            return "Test Provider";
        }
    }

    /**
     * Another test provider with different scheme
     */
    static class AnotherResourceProvider implements ResourceProvider {
        @Override
        public void initialize() {}

        @Override
        public List<Resource> listResources(String cursor) {
            return List.of(
                new TestResource("other://data", "Other Data", "{\"data\":\"other\"}")
            );
        }

        @Override
        public Resource getResource(String uri) {
            if ("other://data".equals(uri)) {
                return new TestResource("other://data", "Other Data", "{\"data\":\"other\"}");
            }
            return null;
        }

        @Override
        public boolean supportsScheme(String scheme) {
            return "other".equals(scheme);
        }

        @Override
        public void shutdown() {}

        @Override
        public String getName() {
            return "Other Provider";
        }
    }

    /**
     * Test implementation of Resource
     */
    static class TestResource implements Resource {
        private final String uri;
        private final String name;
        private final String content;

        TestResource(String uri, String name, String content) {
            this.uri = uri;
            this.name = name;
            this.content = content;
        }

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Test resource: " + name;
        }

        @Override
        public String getMimeType() {
            return "application/json";
        }

        @Override
        public String read() {
            return content;
        }
    }
}

