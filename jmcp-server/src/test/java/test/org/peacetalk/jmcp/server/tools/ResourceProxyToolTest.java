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
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
import org.peacetalk.jmcp.core.model.ListResourcesResult;
import org.peacetalk.jmcp.core.model.ReadResourceResult;
import org.peacetalk.jmcp.core.model.ResourceContents;
import org.peacetalk.jmcp.core.model.ResourceDescriptor;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;
import org.peacetalk.jmcp.server.tools.ResourceProxyTool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResourceProxyTool, the documented workaround that exposes MCP
 * resource navigation through the tools API for clients without native
 * resource support (e.g., GitHub Copilot).
 *
 * The tool builds synthetic JSON-RPC requests and delegates to a real
 * {@link ResourcesHandler}; these tests exercise it end-to-end with hand-written
 * fake {@link ResourceProvider}s/{@link Resource}s registered on that handler
 * (see the "test doubles" section below — both interfaces are small and
 * side-effect-free, so a real implementation is exactly as easy to write as a
 * mock configuration and is easier to read), and assert both the proxied
 * payload contents and the error text surfaced for bad URIs.
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

    private ObjectNode listParams() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "list");
        return params;
    }

    private ObjectNode readParams(String uri) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "read");
        params.put("uri", uri);
        return params;
    }

    @Test
    public void testGetName() {
        assertEquals("resource-proxy", tool.getName());
    }

    @Test
    public void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Workaround for clients without resource support"),
            "description should announce it is a workaround");
        assertTrue(description.contains("ignore if your client supports MCP resources"),
            "description should tell resource-capable clients to ignore it");
    }

    @Test
    public void testGetInputSchema() {
        JsonNode schema = tool.getInputSchema();
        assertNotNull(schema);

        assertEquals("object", schema.get("type").asString(),
            "input schema should be an object schema");

        // Both properties are advertised.
        assertTrue(schema.has("properties"));
        JsonNode properties = schema.get("properties");
        assertTrue(properties.has("operation"), "operation property should be declared");
        assertTrue(properties.has("uri"), "uri property should be declared");

        // Only 'operation' is required; 'uri' is conditionally required (read only)
        // and must NOT be advertised as globally required.
        assertTrue(schema.has("required"));
        JsonNode required = schema.get("required");
        assertTrue(required.isArray());
        assertEquals(1, required.size(), "exactly one field should be globally required");
        assertEquals("operation", required.get(0).asString(),
            "operation should be the only required field");
    }

    @Test
    public void testListResourcesWithNoProviders() throws Exception {
        // No providers registered - the proxy should return an empty (not null) list.
        Object result = tool.execute(listParams());

        assertInstanceOf(ListResourcesResult.class, result);
        ListResourcesResult listResult = (ListResourcesResult) result;
        assertNotNull(listResult.resources());
        assertEquals(0, listResult.resources().size(),
            "no providers registered should yield zero resources");
    }

    @Test
    public void testListResourcesWithProvider() throws Exception {
        Resource resource1 = new FakeResource("test://resource1", "Resource 1", "First test resource",
            "application/json", null);
        Resource resource2 = new FakeResource("test://resource2", "Resource 2", "Second test resource",
            "text/plain", null);
        // Note: the list path aggregates by iterating providers; it does NOT
        // consult supportsScheme, so this fake provider is not given a scheme.
        FakeResourceProvider provider = new FakeResourceProvider(List.of(resource1, resource2));

        resourcesHandler.registerResourceProvider(provider);

        Object result = tool.execute(listParams());

        assertInstanceOf(ListResourcesResult.class, result);
        ListResourcesResult listResult = (ListResourcesResult) result;
        List<ResourceDescriptor> resources = listResult.resources();
        assertEquals(2, resources.size());

        // Each Resource field must be copied into the corresponding descriptor field
        // in order; a mis-mapping (e.g. name<->description swap) would fail here.
        ResourceDescriptor res1 = resources.get(0);
        assertEquals("test://resource1", res1.uri());
        assertEquals("Resource 1", res1.name());
        assertEquals("First test resource", res1.description());
        assertEquals("application/json", res1.mimeType());

        ResourceDescriptor res2 = resources.get(1);
        assertEquals("test://resource2", res2.uri());
        assertEquals("Resource 2", res2.name());
        assertEquals("Second test resource", res2.description());
        assertEquals("text/plain", res2.mimeType());
    }

    @Test
    public void testReadResourceSuccess() throws Exception {
        FakeResource resource = new FakeResource("test://myresource", "application/json", """
                {"data": "test"}""");
        FakeResourceProvider provider = new FakeResourceProvider("test", resource);

        resourcesHandler.registerResourceProvider(provider);

        Object result = tool.execute(readParams("test://myresource"));

        assertInstanceOf(ReadResourceResult.class, result);
        ReadResourceResult readResult = (ReadResourceResult) result;
        assertNotNull(readResult.contents());
        assertEquals(1, readResult.contents().size());

        ResourceContents content = readResult.contents().get(0);
        assertEquals("test://myresource", content.uri());
        assertEquals("application/json", content.mimeType());
        assertEquals("""
                {"data": "test"}""", content.text(),
            "proxied text must equal what the resource returned from read()");
        assertNull(content.blob(),
            "text content must not also carry a blob payload");

        // The tool must actually read the resource, not fabricate content.
        assertTrue(resource.wasRead, "tool must call resource.read()");
    }

    @Test
    public void testReadResourceEmptyContent() throws Exception {
        // Boundary: a resource that reads as an empty string is still valid text
        // content (text != null), so the proxy must surface it rather than error.
        FakeResource resource = new FakeResource("test://empty", "text/plain", "");
        FakeResourceProvider provider = new FakeResourceProvider("test", resource);

        resourcesHandler.registerResourceProvider(provider);

        Object result = tool.execute(readParams("test://empty"));

        assertInstanceOf(ReadResourceResult.class, result);
        ReadResourceResult readResult = (ReadResourceResult) result;
        assertEquals(1, readResult.contents().size());
        assertEquals("", readResult.contents().get(0).text(),
            "empty resource content should be surfaced as an empty string, not dropped");
    }

    @Test
    public void testReadResourceWithoutUri() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "read");
        // No uri provided.

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tool.execute(params)
        );

        assertTrue(exception.getMessage().contains("uri is required"),
            "read without a uri should be rejected before any handler delegation");
    }

    @Test
    public void testReadResourceNotFound() {
        // Provider owns the scheme but returns null for the specific URI.
        FakeResourceProvider provider = new FakeResourceProvider("test");
        resourcesHandler.registerResourceProvider(provider);

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tool.execute(readParams("test://nonexistent"))
        );

        assertTrue(exception.getMessage().contains("Failed to read resource"),
            "proxy should prefix the failure");
        assertTrue(exception.getMessage().contains("Resource not found"),
            "proxy should surface the handler's 'Resource not found' detail, not a generic message");
        assertTrue(exception.getMessage().contains("test://nonexistent"),
            "error should name the URI that could not be found");
    }

    @Test
    public void testReadResourceInvalidUri() {
        // A URI with no scheme cannot be routed; extractScheme returns null.
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tool.execute(readParams("invalid-uri-no-scheme"))
        );

        assertTrue(exception.getMessage().contains("Failed to read resource"),
            "proxy should prefix the failure");
        assertTrue(exception.getMessage().contains("Invalid resource URI"),
            "malformed (schemeless) URI should surface the 'Invalid resource URI' detail");
    }

    @Test
    public void testReadResourceNoProviderForScheme() {
        // No provider registered for the "unknown" scheme.
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tool.execute(readParams("unknown://resource"))
        );

        assertTrue(exception.getMessage().contains("Failed to read resource"),
            "proxy should prefix the failure");
        assertTrue(exception.getMessage().contains("No provider found for URI scheme"),
            "unsupported scheme should surface the 'No provider found' detail");
        assertTrue(exception.getMessage().contains("unknown"),
            "error should name the unsupported scheme");
    }

    @Test
    public void testReadResourceProviderReadThrows() throws Exception {
        // A provider whose read() blows up must be reported as a read failure,
        // not swallowed or surfaced as a successful (empty) result.
        FakeResource resource = FakeResource.thatFailsToRead("test://boom", "text/plain",
            new RuntimeException("disk on fire"));
        FakeResourceProvider provider = new FakeResourceProvider("test", resource);

        resourcesHandler.registerResourceProvider(provider);

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> tool.execute(readParams("test://boom"))
        );

        assertTrue(exception.getMessage().contains("Failed to read resource"),
            "proxy should prefix the failure");
        assertTrue(exception.getMessage().contains("Resource read failed"),
            "handler's internal-error wording for a throwing read() should be surfaced");
    }

    @Test
    public void testInvalidOperation() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "delete"); // Not 'list' or 'read'.

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tool.execute(params)
        );

        assertTrue(exception.getMessage().contains("Unknown operation"),
            "unrecognized operation should be rejected explicitly");
        assertTrue(exception.getMessage().contains("delete"),
            "error should echo the offending operation value");
    }

    @Test
    public void testMissingOperation() {
        ObjectNode params = MAPPER.createObjectNode();
        // No operation provided; params.get("operation") is null so .asString() throws.

        // Documents current behavior: absence of the required field yields an NPE
        // rather than a friendly IllegalArgumentException.
        assertThrows(
            NullPointerException.class,
            () -> tool.execute(params),
            "missing 'operation' currently throws NPE from params.get(...).asString()"
        );
    }

    @Test
    public void testOperationCaseInsensitive() throws Exception {
        // Operation is lower-cased before matching, so any casing of 'list' works.
        Object upper = tool.execute(MAPPER.createObjectNode().put("operation", "LIST"));
        assertInstanceOf(ListResourcesResult.class, upper);

        Object mixed = tool.execute(MAPPER.createObjectNode().put("operation", "LiSt"));
        assertInstanceOf(ListResourcesResult.class, mixed);
    }

    @Test
    public void testReadOperationCaseInsensitive() throws Exception {
        // The read branch must be reached regardless of casing.
        FakeResource resource = new FakeResource("test://r", "text/plain", "hello");
        FakeResourceProvider provider = new FakeResourceProvider("test", resource);

        resourcesHandler.registerResourceProvider(provider);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("operation", "READ");
        params.put("uri", "test://r");

        Object result = tool.execute(params);
        assertInstanceOf(ReadResourceResult.class, result);
        assertEquals("hello", ((ReadResourceResult) result).contents().get(0).text());
    }

    @Test
    public void testListResourcesWithMultipleProviders() throws Exception {
        Resource resource1 = new FakeResource("test1://resource", "Provider 1 Resource", "From provider 1",
            "application/json", null);
        Resource resource2 = new FakeResource("test2://resource", "Provider 2 Resource", "From provider 2",
            "text/plain", null);
        FakeResourceProvider provider1 = new FakeResourceProvider(List.of(resource1));
        FakeResourceProvider provider2 = new FakeResourceProvider(List.of(resource2));

        resourcesHandler.registerResourceProvider(provider1);
        resourcesHandler.registerResourceProvider(provider2);

        Object result = tool.execute(listParams());

        assertInstanceOf(ListResourcesResult.class, result);
        ListResourcesResult listResult = (ListResourcesResult) result;
        assertEquals(2, listResult.resources().size(),
            "resources from every registered provider should be aggregated");

        // Aggregation preserves provider registration order.
        assertEquals("test1://resource", listResult.resources().get(0).uri());
        assertEquals("test2://resource", listResult.resources().get(1).uri());

        assertEquals(1, provider1.listResourcesCalls, "every registered provider must be asked for its resources");
        assertEquals(1, provider2.listResourcesCalls, "every registered provider must be asked for its resources");
    }

    @Test
    public void testReadResourceWithComplexContent() throws Exception {
        // Multi-line JSON must be proxied verbatim (no reformatting/parsing).
        String complexJson = """
            {
                "databases": [
                    {"id": "db1", "name": "Database 1"},
                    {"id": "db2", "name": "Database 2"}
                ],
                "count": 2
            }
            """;

        FakeResource resource = new FakeResource("db://context", "application/json", complexJson);
        FakeResourceProvider provider = new FakeResourceProvider("db", resource);

        resourcesHandler.registerResourceProvider(provider);

        Object result = tool.execute(readParams("db://context"));

        assertInstanceOf(ReadResourceResult.class, result);
        ReadResourceResult readResult = (ReadResourceResult) result;
        assertEquals(complexJson, readResult.contents().get(0).text(),
            "complex content must be proxied byte-for-byte");
    }

    @Test
    public void testListDelegationToResourcesHandler() throws Exception {
        // The list operation must build a resources/list JSON-RPC request and
        // hand it to the ResourcesHandler.
        RecordingResourcesHandler recordingHandler = new RecordingResourcesHandler();
        ResourceProxyTool toolWithRecordingHandler = new ResourceProxyTool(recordingHandler);

        toolWithRecordingHandler.execute(listParams());

        assertNotNull(recordingHandler.lastRequest, "the handler must have been invoked");
        assertEquals("resources/list", recordingHandler.lastRequest.method());
        assertEquals("2.0", recordingHandler.lastRequest.jsonrpc());
    }

    @Test
    public void testReadDelegationForwardsUri() throws Exception {
        // The read operation must build a resources/read request carrying the
        // exact URI the caller supplied; a forwarding bug (wrong/blank uri)
        // would fail on the captured params.
        FakeResource resource = new FakeResource("db://connection/mydb/schemas", "application/json", "{}");
        FakeResourceProvider provider = new FakeResourceProvider("db", resource);

        RecordingResourcesHandler recordingHandler = new RecordingResourcesHandler();
        recordingHandler.registerResourceProvider(provider);
        ResourceProxyTool toolWithRecordingHandler = new ResourceProxyTool(recordingHandler);

        toolWithRecordingHandler.execute(readParams("db://connection/mydb/schemas"));

        assertNotNull(recordingHandler.lastRequest, "the handler must have been invoked");
        assertEquals("resources/read", recordingHandler.lastRequest.method());
        assertEquals("2.0", recordingHandler.lastRequest.jsonrpc());

        // params is an ObjectNode built by the tool; confirm the uri round-trips.
        JsonNode paramsNode = MAPPER.valueToTree(recordingHandler.lastRequest.params());
        assertTrue(paramsNode.has("uri"), "read request params must carry a uri");
        assertEquals("db://connection/mydb/schemas", paramsNode.get("uri").asString(),
            "the caller's URI must be forwarded unchanged to the handler");

        // And the provider must have been asked for that exact deep URI.
        assertEquals("db://connection/mydb/schemas", provider.lastRequestedUri,
            "the provider must be asked for the exact URI the caller supplied");
    }

    // ---- test doubles --------------------------------------------------------
    // Resource and ResourceProvider are small, side-effect-free interfaces, so a
    // real implementation is exactly as easy to write as a mock configuration and
    // is easier to read/debug. See ToolsHandlerTest / ResourcesHandlerTest for the
    // same convention applied to Tool/McpProvider.

    /** A fixed-content Resource, optionally failing on read() to exercise error paths. */
    private static final class FakeResource implements Resource {
        private final String uri;
        private final String name;
        private final String description;
        private final String mimeType;
        private final String content;
        private final RuntimeException readFailure;
        boolean wasRead;

        /** A resource with full metadata, for tests that assert name/description (e.g. listing). */
        FakeResource(String uri, String name, String description, String mimeType, String content) {
            this(uri, name, description, mimeType, content, null);
        }

        /** A resource with just uri/mimeType/content, for read-path tests that don't assert name/description. */
        FakeResource(String uri, String mimeType, String content) {
            this(uri, null, null, mimeType, content, null);
        }

        /** A resource whose read() always throws, to exercise the read-failure path. */
        static FakeResource thatFailsToRead(String uri, String mimeType, RuntimeException failure) {
            return new FakeResource(uri, null, null, mimeType, null, failure);
        }

        private FakeResource(String uri, String name, String description, String mimeType,
                              String content, RuntimeException readFailure) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
            this.content = content;
            this.readFailure = readFailure;
        }

        @Override public String getUri() { return uri; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public String getMimeType() { return mimeType; }

        @Override
        public String read() {
            wasRead = true;
            if (readFailure != null) {
                throw readFailure;
            }
            return content;
        }
    }

    /** A ResourceProvider that either serves a fixed listResources() result, or owns one scheme
     * and serves specific resources by URI (never both, matching how each test actually uses it). */
    private static final class FakeResourceProvider implements ResourceProvider {
        private final String scheme;
        private final List<Resource> listed;
        private final Map<String, Resource> byUri = new HashMap<>();
        int listResourcesCalls;
        String lastRequestedUri;

        /** A provider whose only job is to serve a fixed listResources() result (no scheme routing). */
        FakeResourceProvider(List<Resource> listed) {
            this(null, listed);
        }

        /** A provider that owns {@code scheme} and serves specific resources by URI. */
        FakeResourceProvider(String scheme, Resource... resources) {
            this(scheme, List.of());
            for (Resource r : resources) {
                byUri.put(r.getUri(), r);
            }
        }

        private FakeResourceProvider(String scheme, List<Resource> listed) {
            this.scheme = scheme;
            this.listed = listed;
        }

        @Override
        public void initialize() {}

        @Override
        public List<Resource> listResources(String cursor) {
            listResourcesCalls++;
            return listed;
        }

        @Override
        public Resource getResource(String uri) {
            lastRequestedUri = uri;
            return byUri.get(uri);
        }

        @Override
        public boolean supportsScheme(String s) {
            return scheme != null && scheme.equals(s);
        }

        @Override
        public void shutdown() {}

        @Override
        public String getName() {
            return "fake-provider-" + scheme;
        }
    }

    /** Delegates to the real ResourcesHandler while recording the last request it saw. */
    private static final class RecordingResourcesHandler extends ResourcesHandler {
        JsonRpcRequest lastRequest;

        @Override
        public JsonRpcResponse handle(JsonRpcRequest request) {
            this.lastRequest = request;
            return super.handle(request);
        }
    }
}
