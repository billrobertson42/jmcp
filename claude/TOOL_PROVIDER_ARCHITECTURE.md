# Tool Provider Architecture Implementation

**Date:** December 28, 2025

## Summary

Implemented a clean separation of concerns by introducing `ToolProvider` and `Tool` interfaces in the `jmcp-core` module. This refactoring moves JDBC-specific initialization logic out of the server's Main class and into the `jmcp-jdbc` module, making the architecture more modular and extensible.

## Changes

### New Core Interfaces (jmcp-core)

#### 1. `Tool` Interface
**Location:** `jmcp-core/src/main/java/org/peacetalk/jmcp/core/Tool.java`

Generic interface for all MCP tools with methods:
- `String getName()` - Get the unique tool name
- `String getDescription()` - Get human-readable description  
- `JsonNode getInputSchema()` - Get JSON schema for input parameters
- `Object execute(JsonNode params)` - Execute the tool

This interface abstracts away the implementation details, allowing tools to work with any transport or server implementation.

#### 2. `ToolProvider` Interface
**Location:** `jmcp-core/src/main/java/org/peacetalk/jmcp/core/ToolProvider.java`

Interface for components that provide and manage tools with methods:
- `void initialize(Object configuration)` - Initialize with provider-specific config
- `List<Tool> getTools()` - Get all tools from this provider
- `void shutdown()` - Clean up resources
- `String getName()` - Get provider name

This enables multiple tool providers to coexist and be dynamically loaded.

### JDBC Module Refactoring (jmcp-jdbc)

#### 3. Configuration Classes Moved
- Created `org.peacetalk.jmcp.jdbc.config.JdbcConfiguration` (moved from server)
- Created `org.peacetalk.jmcp.jdbc.config.ConnectionConfig` (moved from server)

These are JDBC-specific and don't belong in the generic server module.

#### 4. `ConnectionContextResolver` Interface
**Location:** `jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/ConnectionContextResolver.java`

Interface for resolving database connection contexts:
- `String getDefaultConnectionId()` - Get default connection ID
- `ConnectionContext getConnectionContext(String connectionId)` - Get connection by ID

This decouples the adapter from the ConnectionManager implementation.

#### 5. `JdbcToolAdapter` Class
**Location:** `jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/JdbcToolAdapter.java`

Adapter that wraps `JdbcTool` instances to implement the generic `Tool` interface. This allows JDBC-specific tools (which need a `ConnectionContext`) to work with the generic tool system. The adapter:
- Resolves connection context from parameters or uses default
- Delegates to the wrapped JdbcTool for execution

#### 6. `JdbcToolProvider` Class
**Location:** `jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/JdbcToolProvider.java`

Implements `ToolProvider` for JDBC tools. This class now handles all JDBC-specific initialization that was previously in Main:
- **Loads its own configuration** from `~/.jmcp/config.json` or `JMCP_CONFIG` environment variable
- Sets up driver cache directory
- Initializes `JdbcDriverManager`
- Initializes `ConnectionManager`
- Registers connections from configuration
- Creates and registers all JDBC tools with adapters

The provider's `initialize()` method accepts `null` as a configuration parameter and will load its own configuration automatically. This keeps the server completely decoupled from JDBC-specific configuration details.

#### 7. `ConnectionManager` Updates
Now implements `ConnectionContextResolver` interface to provide connection resolution capability to the adapter layer.

#### 8. `JdbcToolsHandler` Simplification
Updated to work with generic `ToolProvider` and `Tool` interfaces instead of JDBC-specific types. Now:
- Accepts multiple tool providers
- Iterates over providers' tools for listing and execution
- No longer manages connections directly (delegated to provider)

### Server Module Simplification (jmcp-server)

#### 9. `Main` Class Refactored
**Location:** `jmcp-server/src/main/java/org/peacetalk/jmcp/server/Main.java`

Dramatically simplified to focus only on:
- Creating `JdbcToolProvider` and calling `initialize(null)` (provider loads its own config)
- Setting up MCP server with handlers
- Managing transport lifecycle
- Shutdown coordination

**Zero JDBC-specific logic or knowledge** - completely generic server orchestration. The Main class doesn't even import Jackson or any configuration classes.

#### 10. Configuration Classes Removed
- Deleted `org.peacetalk.jmcp.server.Configuration`
- Deleted `org.peacetalk.jmcp.server.ConnectionConfig`

These are now in `jmcp-jdbc` module where they belong.

## Benefits

### 1. **Separation of Concerns**
- Core module defines generic abstractions
- JDBC module contains all JDBC-specific code (including configuration loading)
- Server module is a thin orchestration layer with no domain knowledge

### 2. **Extensibility**
- Easy to add new tool providers (e.g., REST API tools, file system tools)
- Tool providers can be loaded dynamically
- Multiple providers can coexist

### 3. **Maintainability**
- Each module has clear responsibilities
- JDBC initialization logic is in one place
- Main class is dramatically simplified

### 4. **Testability**
- Tool providers can be tested independently
- Tools can be tested without server infrastructure
- Easier to mock dependencies

### 5. **Modularity**
- Clear module boundaries
- Proper dependency direction (server → jdbc → core)
- Configuration classes live with their implementations

## Architecture Diagram

```
┌─────────────────┐
│   jmcp-server   │  (Thin orchestration layer)
│                 │
│  Main.java      │  - Loads config
│                 │  - Creates providers
│                 │  - Manages transport
└────────┬────────┘
         │ uses
         ▼
┌─────────────────────┐
│    jmcp-jdbc        │  (JDBC implementation)
│                     │
│  JdbcToolProvider   │  - Implements ToolProvider
│  JdbcToolAdapter    │  - Adapts JdbcTool to Tool
│  ConnectionManager  │  - Implements ConnectionContextResolver
│  JdbcToolsHandler   │  - Handles tool RPC calls
│  Configuration      │  - JDBC config models
└────────┬────────────┘
         │ implements
         ▼
┌─────────────────────┐
│     jmcp-core       │  (Generic abstractions)
│                     │
│  Tool               │  - Interface for all tools
│  ToolProvider       │  - Interface for tool providers
│  McpServer          │  - Generic MCP server
│  JsonRpc models     │  - Protocol models
└─────────────────────┘
```

## Migration Path for New Tool Providers

To create a new tool provider (e.g., for file system operations):

1. Create a new module `jmcp-filesystem`
2. Implement `Tool` interface for each file system tool
3. Implement `ToolProvider` to manage file system tools
4. Create configuration model for file system settings
5. Update `Main` to load and initialize the file system provider
6. No changes needed to `jmcp-core`

## Testing

All existing tests pass without modification. The refactoring maintains complete backward compatibility from a functionality perspective.

**Test Results:**
- **jmcp-core**: 59 tests passed
- **jmcp-transport-stdio**: 4 tests passed  
- **jmcp-jdbc**: 324 tests passed
- **jmcp-server**: No tests (server is now a thin orchestration layer)
- **jmcp-client**: 42 tests passed

**Total: 429 tests, all passing ✓**

The refactoring was completed successfully with zero test failures, demonstrating that the new architecture is fully backward compatible with the existing functionality.

---

*"Simplicity is the ultimate sophistication."* - Leonardo da Vinci

