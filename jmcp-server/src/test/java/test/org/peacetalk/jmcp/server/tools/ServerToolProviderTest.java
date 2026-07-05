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
import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.model.ListResourcesResult;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;
import org.peacetalk.jmcp.server.tools.ServerToolProvider;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ServerToolProvider, which supplies protocol-level (non-domain)
 * tools — currently just the resource proxy — and wires that proxy to the
 * ResourcesHandler it is constructed with.
 */
public class ServerToolProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResourcesHandler resourcesHandler;
    private ServerToolProvider provider;

    @BeforeEach
    public void setUp() {
        resourcesHandler = new ResourcesHandler();
        provider = new ServerToolProvider(resourcesHandler);
    }

    @Test
    public void testGetName() {
        assertEquals("Server Tools", provider.getName());
    }

    @Test
    public void testGetTools() {
        List<Tool> tools = provider.getTools();

        assertNotNull(tools);
        assertEquals(1, tools.size(), "provider should expose exactly the resource proxy");

        Tool tool = tools.get(0);
        assertEquals("resource-proxy", tool.getName());
        assertNotNull(tool.getDescription(), "the exposed tool should be a usable, described tool");
    }

    @Test
    public void testConfigure() {
        // The provider needs no configuration, so a null config section must be
        // accepted silently (per the McpProvider contract for providers that
        // require no config).
        assertDoesNotThrow(() -> provider.configure(null));
    }

    @Test
    public void testShutdown() {
        // Nothing to release; shutdown must be a safe no-op.
        assertDoesNotThrow(() -> provider.shutdown());
    }

    @Test
    public void testToolsAreSameInstance() {
        // The proxy is built once at construction; repeated getTools() calls must
        // return the cached instance rather than rebuilding it each time.
        List<Tool> tools1 = provider.getTools();
        List<Tool> tools2 = provider.getTools();

        assertSame(tools1.get(0), tools2.get(0),
            "getTools() should return the same tool instance on repeated calls");
    }

    @Test
    public void testConstructorWithNullHandler() {
        // Construction is lenient: a null handler is stored without validation
        // (it would only fail later when the proxy is actually executed).
        assertDoesNotThrow(() -> new ServerToolProvider(null));
    }

    @Test
    public void testProvidedToolIsWiredToTheGivenHandler() throws Exception {
        // End-to-end: the tool the provider hands out must delegate to the exact
        // ResourcesHandler passed to the constructor. We register a provider on
        // that handler and confirm a 'list' through the tool sees it — catching a
        // wiring bug where the proxy is built against a different/empty handler.
        FakeResource resource = new FakeResource("test://only", "Only Resource", "desc", "text/plain");
        FakeResourceProvider fakeProvider = new FakeResourceProvider(resource);
        resourcesHandler.registerResourceProvider(fakeProvider);

        Tool proxy = provider.getTools().get(0);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "list");
        Object result = proxy.execute(params);

        assertInstanceOf(ListResourcesResult.class, result);
        ListResourcesResult listResult = (ListResourcesResult) result;
        assertEquals(1, listResult.resources().size(),
            "the tool should list resources from the handler it was constructed with");
        assertEquals("test://only", listResult.resources().get(0).uri());
        assertEquals(1, fakeProvider.listResourcesCalls, "the provider must actually be asked for its resources");
    }

    // ---- test doubles --------------------------------------------------------
    // Resource and ResourceProvider are small, side-effect-free interfaces, so a
    // real implementation here is exactly as easy to write as a mock configuration.

    private static final class FakeResource implements Resource {
        private final String uri;
        private final String name;
        private final String description;
        private final String mimeType;

        FakeResource(String uri, String name, String description, String mimeType) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
        }

        @Override public String getUri() { return uri; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public String getMimeType() { return mimeType; }
        @Override public String read() { return ""; }
    }

    private static final class FakeResourceProvider implements ResourceProvider {
        private final List<Resource> resources;
        int listResourcesCalls;

        FakeResourceProvider(Resource... resources) {
            this.resources = List.of(resources);
        }

        @Override public void initialize() {}

        @Override
        public List<Resource> listResources(String cursor) {
            listResourcesCalls++;
            return resources;
        }

        @Override public Resource getResource(String uri) { return null; }
        @Override public boolean supportsScheme(String scheme) { return false; }
        @Override public void shutdown() {}
        @Override public String getName() { return "fake-provider"; }
    }
}
