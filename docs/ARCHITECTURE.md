# JDBC MCP Server - Architecture

## Overview

A modular Java-based Model Context Protocol (MCP) server that provides read-only access to databases via JDBC. The architecture emphasizes modularity, security, extensibility, and zero compile-time coupling between the server and its providers.

## Module Structure

```
jmcp/
├── jmcp-core/              # Core MCP protocol interfaces and SPI contracts
├── jmcp-transport-stdio/   # stdio transport implementation (SPI provider)
├── jmcp-jdbc/              # JDBC tools, resources, connection management, driver loading (SPI provider)
├── jmcp-server/            # Main application assembly (SPI consumer)
└── jmcp-client/            # JavaFX GUI client
```

## Design Principles

### 1. SPI-Based Modular Architecture

Each module has a single, clear responsibility. The server discovers providers at runtime via `java.util.ServiceLoader` — it has **zero compile-time knowledge** of `jmcp-jdbc` or `jmcp-transport-stdio`.

- **jmcp-core**: Protocol abstractions, SPI contracts (`McpProvider`, `TransportProvider`, `Tool`, `Resource`, `ResourceProvider`)
- **jmcp-transport-stdio**: Provides `StdioTransportProvider` via SPI
- **jmcp-jdbc**: Provides `JdbcMcpProvider` (tools + resources) via SPI
- **jmcp-server**: SPI consumer — discovers and assembles providers
- **jmcp-client**: JavaFX GUI for any MCP server

This allows:
- Adding new transports (SSE, WebSocket) without touching server code
- Adding new capability providers without recompiling the server
- Testing components independently
- Building custom JVM images via jlink

### 2. SPI Contracts

**McpProvider** — the central SPI for capability providers:

```java
public interface McpProvider {
    String getName();
    void configure(Map<String, Object> config) throws Exception;
    default List<Tool> getTools() { return Collections.emptyList(); }
    default ResourceProvider getResourceProvider() { return null; }
    void shutdown();
}
```

**TransportProvider** — SPI for transport discovery:

```java
public interface TransportProvider {
    String getName();
    McpTransport createTransport();
    default int priority() { return 0; }
}
```

Registration is via JPMS `module-info.java`:
```java
// In jmcp-jdbc
provides org.peacetalk.jmcp.core.McpProvider
    with org.peacetalk.jmcp.jdbc.JdbcMcpProvider;

// In jmcp-transport-stdio
provides org.peacetalk.jmcp.core.transport.TransportProvider
    with org.peacetalk.jmcp.transport.stdio.StdioTransportProvider;

// In jmcp-server (consumer)
uses org.peacetalk.jmcp.core.McpProvider;
uses org.peacetalk.jmcp.core.transport.TransportProvider;
```

### 3. Two-Tier Tool Interface

The core defines a generic `Tool` interface, while domain modules define specialized interfaces:

```java
// Core - generic, transport-agnostic
public interface Tool {
    String getName();
    String getDescription();
    JsonNode getInputSchema();
    Object execute(JsonNode params) throws Exception;
}

// JDBC module - domain-specific, with connection context
public interface JdbcTool {
    String getName();
    String getDescription();
    JsonNode getInputSchema();
    Object execute(JsonNode params, ConnectionContext context) throws Exception;
}
```

`JdbcToolAdapter` bridges the two, resolving connection context from request parameters:

```java
public class JdbcToolAdapter implements Tool {
    public Object execute(JsonNode params) throws Exception {
        String connectionId = params.has("database_id") ?
            params.get("database_id").asString() :
            contextResolver.getDefaultConnectionId();
        ConnectionContext context = contextResolver.getConnectionContext(connectionId);
        return jdbcTool.execute(params, context);
    }
}
```

### 4. Transport Abstraction

```java
public interface McpTransport {
    void start(McpRequestHandler handler) throws Exception;
    void stop() throws Exception;
    boolean isRunning();
}
```

Benefits:
- Protocol logic independent of transport mechanism
- Easy to add HTTP/SSE/WebSocket transports via SPI
- Testable without I/O dependencies

### 5. Resource System

Resources provide navigable, HATEOAS-like database metadata:

```java
public interface Resource {
    String getUri();      // e.g., "db://connection/mydb/schema/public/tables"
    String getName();
    String getDescription();
    String getMimeType();
    String read() throws Exception;
}

public interface ResourceProvider {
    void initialize() throws Exception;
    List<Resource> listResources(String cursor);
    Resource getResource(String uri);
    boolean supportsScheme(String scheme);
    void shutdown();
    String getName();
}
```

The JDBC module provides 14 resource classes for navigating database structure:

| Resource Class | URI Pattern | Description |
|---|---|---|
| `ContextResource` | `db://context` | Complete database overview |
| `ConnectionsListResource` | `db://connections` | List all connections |
| `ConnectionResource` | `db://connection/{id}` | Connection details |
| `SchemasListResource` | `db://connection/{id}/schemas` | List schemas |
| `SchemaResource` | `db://connection/{id}/schema/{name}` | Schema details |
| `TablesListResource` | `db://connection/{id}/schema/{name}/tables` | List tables |
| `TableResource` | `db://connection/{id}/schema/{name}/table/{name}` | Table metadata |
| `ViewsListResource` | `db://connection/{id}/schema/{name}/views` | List views |
| `ViewResource` | `db://connection/{id}/schema/{name}/view/{name}` | View details |
| `ProcedureResource` | `db://connection/{id}/schema/{name}/procedure/{name}` | Procedure details |
| `RelationshipsResource` | `db://connection/{id}/schema/{name}/table/{name}/relationships` | FK relationships |
| `SchemaRelationshipsResource` | `db://connection/{id}/schema/{name}/relationships` | Schema-wide relationships |

A `ResourceProxyTool` in `jmcp-server` bridges resources to clients that don't support the MCP resources protocol (e.g., IDE Copilots), exposing resource navigation through the tools API.

### 6. Read-Only Safety

Multiple layers of protection:

1. **SQL Validation**: JSqlParser-based AST validation rejects non-SELECT statements
2. **String-Based Pre-checks**: Pattern detection for sequences, index hints, stored procedures, SELECT INTO
3. **CTE Inspection**: Detects DML hidden inside Common Table Expressions
4. **Connection Pool**: HikariCP configured for read-only connections
5. **Query Limits**: Max rows (1000) and timeout (30s)
6. **Fail-Safe**: Parse failures treated as invalid — when in doubt, reject

### 7. Dynamic Driver Loading

Drivers downloaded from Maven Central on-demand:

```
~/.jmcp/
└── drivers/
    ├── postgresql-42.7.1.jar
    ├── mysql-connector-j-8.3.0.jar
    └── ...
```

- `JdbcDriverManager` handles download, caching, and classloader creation
- `MavenCoordinates` identifies driver artifacts
- Each driver runs in an isolated `URLClassLoader` to prevent conflicts

## Component Interactions

```
┌─────────────────┐
│   MCP Client    │
└────────┬────────┘
         │ JSON-RPC over stdio
         ↓
┌─────────────────────────────────────┐
│       StdioTransport                │
│  (reads stdin, writes stdout)       │
└────────┬────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────┐
│          McpServer                   │
│  (HashMap dispatch to handlers)     │
└────────┬────────────────────────────┘
         │
         ├→ InitializationHandler
         ├→ ToolsHandler ──→ [McpProvider] ──→ Tool.execute()
         │       ├→ JdbcMcpProvider (QueryTool, ExplainQueryTool, ...)
         │       └→ ServerToolProvider (ResourceProxyTool)
         └→ ResourcesHandler ──→ [ResourceProvider] ──→ Resource.read()
                  └→ JdbcResourceProvider (14 resource classes)
                           │
                  ┌────────┴─────────┐
                  │ ConnectionManager │
                  │  (pools, isolated)│
                  └──────────┬───────┘
                             │
                  ┌──────────┴──────────┐
                  │                     │
                  ↓                     ↓
             ┌─────────┐         ┌─────────┐
             │  Pool 1 │         │  Pool 2 │
             │ +Driver │         │ +Driver │
             │   CL    │         │   CL    │
             └─────────┘         └─────────┘
```

## Key Classes

### Core Module (`jmcp-core`)

**SPI Contracts:**
- **`McpProvider`**: SPI interface for capability providers (tools + resources)
- **`Tool`**: Generic tool interface (`execute(JsonNode)`)
- **`Resource`**: Resource interface with URI-based navigation
- **`ResourceProvider`**: Resource collection provider
- **`TransportProvider`**: SPI for transport implementations
- **`McpTransport`**: Transport abstraction (start/stop/isRunning)
- **`McpRequestHandler`**: JSON-RPC request processor

**Protocol:**
- **`McpServer`**: HashMap-based method dispatch to handlers
- **`McpProtocolHandler`**: Handler interface (`handle()`, `getSupportedMethods()`)
- **`InitializationHandler`**: MCP handshake
- **`ToolsHandler`**: Aggregates tools from providers, handles `tools/list` and `tools/call`
- **`ResourcesHandler`**: Aggregates resources from providers, handles `resources/list` and `resources/read`

**Models:**
- **`JsonRpcRequest/Response/Error`**: JSON-RPC 2.0 protocol models
- **`InitializeRequest/Result`**: MCP handshake models
- **`ServerCapabilities/ClientCapabilities`**: Capability negotiation
- **`Tool` (model)**: Tool descriptor for `tools/list` responses
- **`CallToolRequest/Result`**: Tool invocation models
- **`ListToolsResult`**: Tool listing response
- **`ResourceDescriptor`**: Resource descriptor for listing
- **`ListResourcesResult`**: Resource listing response
- **`ReadResourceRequest/Result`**: Resource reading models
- **`ResourceContents`**: Resource content wrapper
- **`Content`**: MCP content block

**Schema & Validation:**
- **`ObjectSchema/StringProperty/IntegerProperty/BooleanProperty/ArrayProperty`**: JSON Schema builders
- **`McpValidator`**: JSR-380 (Jakarta Validation) for model validation

### Transport Module (`jmcp-transport-stdio`)

- **`StdioTransport`**: stdio implementation with buffered I/O
- **`StdioTransportProvider`**: SPI provider for stdio transport

### JDBC Module (`jmcp-jdbc`)

**Provider & Adapters:**
- **`JdbcMcpProvider`**: McpProvider SPI implementation
- **`JdbcTool`**: Domain-specific tool interface with `ConnectionContext`
- **`JdbcToolAdapter`**: Bridges `JdbcTool` → `Tool` with context resolution
- **`ConnectionContextResolver`**: Interface for resolving connection context

**Connection Management:**
- **`ConnectionManager`**: Pool management with classloader isolation
- **`ConnectionContext`**: Per-connection execution context
- **`JdbcDriverManager`**: Downloads and caches JDBC drivers
- **`MavenCoordinates`**: Maven artifact identification
- **`JdbcUrlSanitizer`**: Sanitizes URLs for safe logging

**Configuration:**
- **`JdbcConfiguration`**: Top-level JDBC config record
- **`ConnectionConfig`**: Per-connection config record

**Tools:**
- **`QueryTool`**: Execute SELECT queries (up to 1000 rows)
- **`ExplainQueryTool`**: Get execution plans
- **`GetRowCountTool`**: Exact row counts
- **`SampleDataTool`**: Sample rows (first/random/last, max 100)
- **`AnalyzeColumnTool`**: Column statistics (distinct count, nulls, min/max, top values)
- **`JdbcToolUtils`**: Shared utility methods

**Resources:**
- **`JdbcResourceProvider`**: ResourceProvider implementation
- 14 resource classes (see Resource System section above)
- **`TopologicalSort/TopologicalSortResult`**: For FK relationship ordering

**Validation:**
- **`ReadOnlySqlValidator`**: Three-layer SQL validation (string pre-checks → JSqlParser AST → fail-safe)

### Server Module (`jmcp-server`)

- **`Main`**: Entry point — SPI discovery, config loading, server assembly
- **`ServerToolProvider`**: McpProvider for server-level tools
- **`ResourceProxyTool`**: Bridge for clients without resource support

### Client Module (`jmcp-client`)

- **`McpClientApp`**: JavaFX application entry point
- **`McpClientController`**: FXML controller
- **`McpClient`**: MCP protocol handler
- **`StdioClientTransport`**: Process management and stdio I/O
- **`ClientPreferences`**: Persistent user preferences
- **`AccessibilityHelper`**: Accessibility support
- **`CommunicationListener/DisplayContent/DisplayResult`**: Communication interfaces
- **`service/McpService`**: Service layer
- **`service/CommunicationLogger`**: Protocol logging
- **`ui/`**: 7 UI classes (ToolArgumentFormBuilder, ToolListCell, ResourceListCell, NavigableResourceView, NavigableUriDetector, ResourceNavigationHistory, ValueParser)

## Configuration Flow

```
1. Main.main()
2. Load config from (first match wins):
   a. System property: -Djmcp.config=/path/to/config.json
   b. ~/.jmcp/config.json
   c. Environment variable: JMCP_CONFIG (JSON string, not a file path)
3. ServiceLoader discovers TransportProvider (highest priority wins)
4. ServiceLoader discovers McpProvider instances
5. For each McpProvider:
   a. Look up config section by JPMS module name
   b. Call provider.configure(configSection) — fail fast on error
6. Assemble McpServer:
   a. Create ToolsHandler, register providers with tools
   b. Create ResourcesHandler, register resource providers
   c. Create ResourceProxyTool bridge if resources exist
   d. Register InitializationHandler, ToolsHandler, ResourcesHandler
7. Create transport, start transport with McpServer
```

## Request Processing Flow

```
1. StdioTransport reads line from stdin
2. Passes to McpServer.handleRequest()
3. McpServer:
   a. Parses JSON-RPC
   b. Notification (null id)? Process and return null
   c. O(1) HashMap lookup for handler by method name
   d. Invokes handler.handle()
4. Handler (e.g., ToolsHandler):
   a. tools/list → iterates providers, builds tool list
   b. tools/call → O(1) index lookup by tool name, calls tool.execute()
5. Tool (e.g., JdbcToolAdapter):
   a. Resolves ConnectionContext from database_id param
   b. Delegates to JdbcTool.execute(params, context)
   c. Returns result as serializable object
6. Response serialized to JSON
7. StdioTransport writes to stdout
```

## Extension Points

### Adding a New Tool Provider (SPI)

```java
// 1. Implement McpProvider
public class MyProvider implements McpProvider {
    @Override public String getName() { return "My Tools"; }
    @Override public void configure(Map<String, Object> config) { /* ... */ }
    @Override public List<Tool> getTools() { return List.of(new MyTool()); }
    @Override public void shutdown() { }
}

// 2. Register in module-info.java
provides org.peacetalk.jmcp.core.McpProvider
    with com.example.MyProvider;
```

### Adding a New Transport (SPI)

```java
public class SseTransportProvider implements TransportProvider {
    @Override public String getName() { return "sse"; }
    @Override public McpTransport createTransport() { return new SseTransport(); }
    @Override public int priority() { return 10; }
}

// Register in module-info.java
provides org.peacetalk.jmcp.core.transport.TransportProvider
    with com.example.SseTransportProvider;
```

### Adding a New Database Type

Add Maven coordinates in `JdbcDriverManager.KNOWN_DRIVERS`:

```java
Map.entry("mydb", new MavenCoordinates("com.vendor", "mydb-jdbc", "1.0.0"))
```

## Thread Safety

- **McpServer**: `HashMap` dispatch table (populated at startup, immutable after)
- **ToolsHandler**: `HashMap` tool index (populated during registration)
- **ResourcesHandler**: `ArrayList` of providers (populated at startup)
- **ConnectionManager**: `ConcurrentHashMap` for connection pools
- **HikariCP**: Thread-safe connection pooling
- **ClassLoader Isolation**: Each driver pool has a dedicated classloader

## Error Handling

- JSON parse errors → `-32700` (Parse error)
- Unknown method → `-32601` (Method not found)
- Invalid params → `-32602` (Invalid params)
- Execution errors → `-32603` (Internal error)
- SQL validation failures → descriptive violation messages returned to LLM for self-correction
- Provider `configure()` failure → full stack trace, server exits immediately

## Performance Characteristics

- **Cold Start**: 1-2 seconds (driver download on first use)
- **Warm Start**: <100ms (cached drivers)
- **Query Overhead**: ~1-5ms (JSON serialization + SQL validation)
- **Memory**: ~50MB base + ~10MB per driver + connection pools
- **Concurrency**: HikariCP handles concurrent queries efficiently
- **Dispatch**: O(1) HashMap lookup for method routing and tool selection

## Logging

The project uses **Log4j2** as the primary logging framework (`org.apache.logging.log4j`). SLF4J, JUL, Commons Logging, and Log4j 1.x are all bridged to Log4j2 via bridge modules.

---

*Updated: April 14, 2026*
