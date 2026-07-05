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

package test.org.peacetalk.jmcp.core.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
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

        assertThatJson(mapper.writeValueAsString(response.result()))
            .isEqualTo("""
                    {"resources":[
                        {"uri":"test://root","name":"Test Root","description":"Test resource: Test Root","mimeType":"application/json"},
                        {"uri":"test://child","name":"Test Child","description":"Test resource: Test Child","mimeType":"application/json"}
                    ]}""");
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

        // .isString() before the final isEqualTo() is required for the "text" node:
        // its value is itself JSON-shaped ({"data":"root"}), so without .isString()
        // json-unit would try to re-parse the expected argument as JSON instead of
        // comparing it as a plain string.
        assertThatJson(mapper.writeValueAsString(response.result())).and(
            j -> j.node("contents").isArray().hasSize(1),
            j -> j.node("contents[0].uri").isEqualTo("test://root"),
            j -> j.node("contents[0].mimeType").isEqualTo("application/json"),
            j -> j.node("contents[0].text").isString().isEqualTo("{\"data\":\"root\"}")
        );
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

        // Aggregates both providers: 2 from testProvider + 1 from the other = exactly 3.
        assertThatJson(mapper.writeValueAsString(response.result()))
            .node("resources").isArray()
            .hasSize(3)
            .extracting("uri")
            .contains("other://data");
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

        // The content must come from the SECOND provider, not be misrouted to the first.
        assertThatJson(mapper.writeValueAsString(response.result())).and(
            j -> j.node("contents[0].uri").isEqualTo("other://data"),
            j -> j.node("contents[0].text").isString()
                .describedAs("read must be dispatched to the provider whose scheme matches the URI")
                .isEqualTo("{\"data\":\"other\"}")
        );
    }

    @Test
    void testHandleReadResourceMissingUri() {
        // No "uri" param at all: the request cannot name a resource, so it must be
        // rejected with an error (not a spurious success or an NPE) and no result.
        ObjectNode params = mapper.createObjectNode();
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/read", params);

        JsonRpcResponse response = handler.handle(request);

        assertNotNull(response);
        assertNull(response.result(), "a request with no uri must not produce a result");
        assertNotNull(response.error());
        // Current behavior: deserializing params without a uri fails and is reported
        // as a generic internal error (-32603). Arguably a missing required param
        // would be better reported as invalid-params (-32602); this pins the
        // present behavior so any such hardening surfaces as a deliberate change.
        assertEquals(-32603, response.error().code());
    }

    @Test
    void testHandleReadResourceReadThrows() {
        // A provider whose resource.read() throws a runtime error must surface as a
        // JSON-RPC internal error, not be swallowed or reported as success.
        ResourcesHandler boomHandler = new ResourcesHandler();
        boomHandler.registerResourceProvider(new ThrowingResourceProvider());

        ObjectNode params = mapper.createObjectNode();
        params.put("uri", "boom://x");
        JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "resources/read", params);

        JsonRpcResponse response = boomHandler.handle(request);

        assertNotNull(response);
        assertNotNull(response.error());
        assertEquals(-32603, response.error().code(), "a read() failure is an internal error");
        assertTrue(response.error().message().contains("Resource read failed"),
            "message should indicate the read failed, but was: " + response.error().message());
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
                new TestResource("test://root", "Test Root", """
                {"data":"root"}"""),
                new TestResource("test://child", "Test Child", """
                {"data":"child"}""")
            );
        }

        @Override
        public Resource getResource(String uri) {
            if ("test://root".equals(uri)) {
                return new TestResource("test://root", "Test Root", """
                {"data":"root"}""");
            } else if ("test://child".equals(uri)) {
                return new TestResource("test://child", "Test Child", """
                {"data":"child"}""");
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
                new TestResource("other://data", "Other Data", """
                {"data":"other"}""")
            );
        }

        @Override
        public Resource getResource(String uri) {
            if ("other://data".equals(uri)) {
                return new TestResource("other://data", "Other Data", """
                {"data":"other"}""");
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
     * Provider whose resource read() always throws, to exercise the handler's
     * internal-error path.
     */
    static class ThrowingResourceProvider implements ResourceProvider {
        @Override
        public void initialize() {}

        @Override
        public List<Resource> listResources(String cursor) {
            return List.of();
        }

        @Override
        public Resource getResource(String uri) {
            return new Resource() {
                @Override public String getUri() { return uri; }
                @Override public String getName() { return "boom"; }
                @Override public String getDescription() { return "always fails"; }
                @Override public String getMimeType() { return "application/json"; }
                @Override public String read() { throw new RuntimeException("kaboom"); }
            };
        }

        @Override
        public boolean supportsScheme(String scheme) {
            return "boom".equals(scheme);
        }

        @Override
        public void shutdown() {}

        @Override
        public String getName() {
            return "Throwing Provider";
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

