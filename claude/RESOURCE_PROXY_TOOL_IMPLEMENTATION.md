# Resource Proxy Tool Implementation - February 16, 2026

## Purpose
Provides a tool-based proxy for MCP resources to support clients that don't implement the resources protocol (e.g., GitHub Copilot).

## Implementation

### Design Decision: Pass-Through Architecture
The `ResourceProxyTool` delegates directly to the existing `ResourcesHandler` instead of duplicating resource logic. This ensures:
- **No code duplication** - All resource logic remains in one place
- **Consistency** - Tool users get identical results to resource users
- **Maintainability** - Changes to resource handling only need to be made once
- **Proper MCP models** - Returns `ListResourcesResult` and `ReadResourceResult` directly

### Architecture

```
Client (no resource support)
    ↓
  tools/call → resource-proxy
    ↓
  ResourceProxyTool
    ↓
  Creates synthetic JSON-RPC request
    ↓
  ResourcesHandler.handle()
    ↓
  Returns MCP protocol response
    ↓
  Result returned to client
```

## Files

### 1. ResourceProxyTool.java
**Location:** `/jmcp-server/src/main/java/org/peacetalk/jmcp/server/tools/ResourceProxyTool.java`

**Key Features:**
- Accepts `operation` parameter: `"list"` or `"read"`
- For `list`: Creates `resources/list` JSON-RPC request and delegates to handler
- For `read`: Creates `resources/read` JSON-RPC request with URI and delegates to handler
- Returns standard MCP model classes (`ListResourcesResult`, `ReadResourceResult`)
- No custom record types - reuses existing protocol models

**Constructor:**
```java
public ResourceProxyTool(ResourcesHandler resourcesHandler)
```

### 2. ServerToolProvider.java
**Location:** `/jmcp-server/src/main/java/org/peacetalk/jmcp/server/tools/ServerToolProvider.java`

Wraps `ResourceProxyTool` as a `ToolProvider` for registration with `ToolsHandler`.

**Constructor:**
```java
public ServerToolProvider(ResourcesHandler resourcesHandler)
```

### 3. Main.java Updates
**Location:** `/jmcp-server/src/main/java/org/peacetalk/jmcp/server/Main.java`

Registers the `ServerToolProvider` after creating the `ResourcesHandler`:

```java
ResourcesHandler resourcesHandler = new ResourcesHandler();
resourcesHandler.registerResourceProvider(resourceProvider);
mcpServer.registerHandler(resourcesHandler);

ServerToolProvider serverToolProvider = new ServerToolProvider(resourcesHandler);
toolsHandler.registerToolProvider(serverToolProvider);
```

### 4. module-info.java Updates
**Location:** `/jmcp-server/src/main/java/module-info.java`

```java
requires tools.jackson.databind;
exports org.peacetalk.jmcp.server.tools;
```

## Usage

### List All Resources
```json
{
  "name": "resource-proxy",
  "arguments": {
    "operation": "list"
  }
}
```

**Returns:** Standard MCP `ListResourcesResult`
```json
{
  "resources": [
    {
      "uri": "db://context",
      "name": "Database Context Summary",
      "description": "Complete overview: all connections, schemas, tables, tools, and resources.",
      "mimeType": "application/json"
    },
    ...
  ]
}
```

### Read a Resource
```json
{
  "name": "resource-proxy",
  "arguments": {
    "operation": "read",
    "uri": "db://context"
  }
}
```

**Returns:** Standard MCP `ReadResourceResult`
```json
{
  "contents": [
    {
      "uri": "db://context",
      "mimeType": "application/json",
      "text": "{...resource content...}"
    }
  ]
}
```

## Benefits

### For Resource-Unaware Clients
- Can access all resource functionality through standard tools API
- No need to implement resources protocol
- Clear indication in tool description that it's a workaround

### For Resource-Aware Clients
- Tool description starts with `[Workaround for clients without resource support - ignore if your client supports MCP resources]`
- Can safely ignore this tool and use native resources protocol
- No behavioral differences between tool and resource access

### For Maintainers
- Single source of truth for resource logic
- No duplication between tools and resources
- Changes to resource handling automatically reflected in proxy tool
- Proper MCP protocol models used throughout

## Testing

Compilation successful. The tool:
1. Properly delegates to `ResourcesHandler`
2. Creates valid JSON-RPC requests
3. Returns standard MCP protocol responses
4. Handles errors by re-throwing with clear messages

## Future Considerations

If more clients adopt the resources protocol, this tool can be:
- Kept as-is for backward compatibility
- Deprecated with a note in the description
- Eventually removed when no longer needed

The pass-through architecture means it has minimal maintenance burden regardless.

