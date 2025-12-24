# JDBC MCP Server - Architecture

## Overview

A modular Java-based Model Context Protocol (MCP) server that provides read-only access to databases via JDBC. The architecture emphasizes modularity, security, and extensibility.

## Module Structure

```
jmcp/
├── jmcp-core/              # Core MCP protocol interfaces
├── jmcp-transport-stdio/   # stdio transport implementation
├── jmcp-jdbc/              # JDBC tools, connection management, and driver loading
├── jmcp-server/            # Main application assembly
└── jmcp-client/            # JavaFX GUI client
```

## Design Principles

### 1. Modular Architecture

Each module has a single, clear responsibility:

- **jmcp-core**: Protocol abstractions (transport-agnostic)
- **jmcp-transport-stdio**: stdio-specific implementation
- **jmcp-jdbc**: Database operations, connection management, and dynamic driver loading
- **jmcp-server**: Wiring and configuration
- **jmcp-client**: JavaFX GUI for MCP servers

This allows:
- Adding new transports (SSE, WebSocket) without touching core logic
- Testing components independently
- Future jlink distribution for minimal runtime

### 2. Transport Abstraction

```java
public interface McpTransport {
    void start(McpRequestHandler handler) throws Exception;
    void stop() throws Exception;
    boolean isRunning();
}
```

Benefits:
- Protocol logic independent of transport mechanism
- Easy to add HTTP/SSE/WebSocket transports
- Testable without I/O dependencies

### 3. Isolated ClassLoaders

Each JDBC driver runs in its own `URLClassLoader`:

```java
public class DriverClassLoader extends URLClassLoader {
    public DriverClassLoader(Path jarPath) throws Exception {
        super(new URL[]{jarPath.toUri().toURL()}, 
              ClassLoader.getPlatformClassLoader());
    }
}
```

Benefits:
- No version conflicts between different drivers
- Drivers can be loaded/unloaded dynamically
- Each connection isolated from others
- Supports multiple database types simultaneously

### 4. Read-Only Safety

Multiple layers of protection:

1. **Query Validation**: Only SELECT queries allowed
2. **Connection Pool**: Read-only flag set on connections
3. **Prepared Statements**: SQL injection prevention
4. **Query Limits**: Max rows (1000) and timeout (30s)

### 5. Dynamic Driver Loading

Drivers downloaded from Maven Central on-demand:

```
~/.jmcp/
└── drivers/
    ├── postgresql-42.7.1.jar
    ├── mysql-connector-j-8.3.0.jar
    └── ...
```

Benefits:
- Small distribution size (no bundled drivers)
- Support for any JDBC driver via Maven coordinates
- Automatic version management
- Cache for offline use

## Component Interactions

```
┌─────────────────┐
│  Claude Desktop │
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
│  (routes to protocol handlers)      │
└────────┬────────────────────────────┘
         │
         ├→ InitializationHandler
         └→ JdbcToolsHandler
                   │
                   ↓
         ┌─────────────────────┐
         │  ConnectionManager  │
         │  (pools, isolated)  │
         └──────────┬──────────┘
                    │
         ┌──────────┴──────────┐
         │                     │
         ↓                     ↓
    ┌─────────┐         ┌─────────┐
    │  Pool 1 │         │  Pool 2 │
    │ + Driver│         │ + Driver│
    │   CL    │         │   CL    │
    └─────────┘         └─────────┘
```

## Key Classes

### Core Module

- **`McpTransport`**: Transport abstraction interface
- **`McpRequestHandler`**: JSON-RPC request processor
- **`McpProtocolHandler`**: Handler for specific MCP methods
- **`McpServer`**: Request dispatcher
- **`JsonRpcRequest/Response/Error`**: Protocol models

### Transport Module

- **`StdioTransport`**: stdio implementation with buffered I/O

### JDBC Module

- **`JdbcDriverManager`**: Downloads and caches drivers
- **`DriverCoordinates`**: Maven artifact identification
- **`DriverClassLoader`**: Isolated driver loading

- **`JdbcTool`**: Interface for database tools
- **`ConnectionManager`**: Pool management with isolation
- **`ConnectionContext`**: Per-connection execution context
- **`JdbcToolsHandler`**: MCP handler for tool operations

### Tool Implementations

- **`QueryTool`**: Execute SELECT queries
- **`ListTablesTool`**: Discover tables
- **`ListSchemasTool`**: Discover schemas
- **`DescribeTableTool`**: Table metadata
- **`GetRowCountTool`**: Count rows
- **`PreviewTableTool`**: Sample data

## Configuration Flow

```
1. Main.main()
2. Load config from:
   - ~/.jmcp/config.json
   - OR jmcp_CONFIG env var
3. For each connection:
   a. JdbcDriverManager downloads driver (if needed)
   b. DriverClassLoader created
   c. HikariCP pool initialized
   d. ConnectionContext registered
4. Tools registered with handler
5. Handlers registered with McpServer
6. StdioTransport started
```

## Request Processing Flow

```
1. StdioTransport reads line from stdin
2. Passes to McpServer.handleRequest()
3. McpServer:
   a. Parses JSON-RPC
   b. Finds matching handler
   c. Invokes handler.handle()
4. Handler (e.g., JdbcToolsHandler):
   a. Extracts parameters
   b. Finds tool
   c. Gets ConnectionContext
   d. Executes tool.execute()
5. Tool:
   a. Gets connection from pool
   b. Executes SQL via JDBC
   c. Returns result as JSON
6. Response serialized to JSON
7. StdioTransport writes to stdout
```

## Extension Points

### Adding a New Tool

```java
public class MyTool implements JdbcTool {
    @Override
    public String getName() { return "my-tool"; }
    
    @Override
    public String getDescription() { return "..."; }
    
    @Override
    public JsonNode getInputSchema() { /* JSON Schema */ }
    
    @Override
    public Object execute(JsonNode params, ConnectionContext context) {
        try (Connection conn = context.getConnection()) {
            // Your logic here
        }
    }
}

// Register in Main.java:
toolsHandler.registerTool(new MyTool());
```

### Adding a New Transport

```java
public class SseTransport implements McpTransport {
    @Override
    public void start(McpRequestHandler handler) {
        // Start HTTP server
        // Handle GET for SSE stream (server -> client)
        // Handle POST for requests (client -> server)
    }
    
    // ...
}

// Use in Main.java:
McpTransport transport = new SseTransport(port);
transport.start(mcpServer);
```

### Adding a New Database Type

Edit `JdbcDriverManager.KNOWN_DRIVERS`:

```java
Map.entry("mydb", new DriverCoordinates(
    "com.vendor", "mydb-jdbc", "1.0.0"
))
```

Add driver class name in `ConnectionManager.getDriverClassName()`:

```java
case "mydb" -> "com.vendor.mydb.Driver";
```

## Thread Safety

- **ConnectionManager**: Uses `ConcurrentHashMap` for pools
- **JdbcToolsHandler**: Uses `CopyOnWriteArrayList` for tools
- **McpServer**: Handler list is copy-on-write
- **HikariCP**: Thread-safe connection pooling
- **ClassLoader Isolation**: Each pool has dedicated classloader

## Error Handling

- JSON parse errors → `-32700` (Parse error)
- Unknown method → `-32601` (Method not found)
- Invalid params → `-32602` (Invalid params)
- Execution errors → `-32603` (Internal error)
- SQL errors wrapped with descriptive messages

## Performance Characteristics

- **Cold Start**: 1-2 seconds (driver download on first use)
- **Warm Start**: <100ms (cached drivers)
- **Query Overhead**: ~1-5ms (JSON serialization)
- **Memory**: ~50MB base + ~10MB per driver + connection pools
- **Concurrency**: HikariCP handles concurrent queries efficiently

## Future Enhancements

1. **Resources Support**: Expose database schemas as MCP resources
2. **Prompts Support**: Pre-defined query templates
3. **SSE Transport**: For web-based clients
4. **Metrics**: Query performance tracking
5. **Query Caching**: Cache frequent query results
6. **Write Operations**: Optional support for INSERT/UPDATE (with explicit configuration)
7. **Transaction Support**: Multi-query transactions (read-only)
8. **Custom Tools**: Plugin system for user-defined tools

