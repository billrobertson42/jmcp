# McpProtocolHandler Optimization - HashMap Dispatch

**Date:** December 28, 2025

## Important Architectural Note

This optimization initially had a design flaw that was corrected during implementation: **Multiple ToolProviders would all want to handle `tools/list` and `tools/call`**, so we can't have separate handlers for each provider.

**Solution:** There is ONE generic `ToolsHandler` in `jmcp-core` that:
- Registers with methods `tools/list` and `tools/call` in the HashMap
- Internally manages multiple ToolProviders
- Aggregates tools from all providers for `tools/list`
- Searches across all providers to find the right tool for `tools/call`

This way:
- The HashMap dispatch remains O(1) for protocol-level routing
- The ToolsHandler internally handles provider-level dispatch  
- Multiple tool providers (JDBC, file system, REST API, etc.) can coexist

## Summary

Refactored `McpProtocolHandler` and `McpServer` to use O(1) HashMap-based method dispatch instead of O(n) linear search through handlers for each request. Additionally, moved the generic `ToolsHandler` from `jmcp-jdbc` to `jmcp-core` where it belongs, since it works with any ToolProvider.

## Changes Made

### 1. McpProtocolHandler Interface

**Before:**
```java
public interface McpProtocolHandler {
    JsonRpcResponse handle(JsonRpcRequest request);
    boolean canHandle(String method);  // Called for EVERY request
}
```

**After:**
```java
public interface McpProtocolHandler {
    JsonRpcResponse handle(JsonRpcRequest request);
    Set<String> getSupportedMethods();  // Called ONCE during registration
}
```

### 2. McpServer Dispatch Mechanism

**Before - O(n) Linear Search:**
```java
private List<McpProtocolHandler> handlers;

private JsonRpcResponse processRequest(JsonRpcRequest request) {
    for (McpProtocolHandler handler : handlers) {
        if (handler.canHandle(request.method())) {  // O(n) search
            return handler.handle(request);
        }
    }
    return JsonRpcResponse.error(...);
}
```

**After - O(1) HashMap Lookup:**
```java
private Map<String, McpProtocolHandler> methodHandlers;

public void registerHandler(McpProtocolHandler handler) {
    for (String method : handler.getSupportedMethods()) {  // Build dispatch table once
        if (methodHandlers.containsKey(method)) {
            throw new IllegalStateException("Method already registered");
        }
        methodHandlers.put(method, handler);
    }
}

private JsonRpcResponse processRequest(JsonRpcRequest request) {
    McpProtocolHandler handler = methodHandlers.get(request.method());  // O(1) lookup
    if (handler == null) {
        return JsonRpcResponse.error(...);
    }
    return handler.handle(request);
}
```

### 3. Generic ToolsHandler Moved to Core

**Old Location:** `jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/JdbcToolsHandler.java`  
**New Location:** `jmcp-core/src/main/java/org/peacetalk/jmcp/core/protocol/ToolsHandler.java`

**Why:** The handler is completely generic and works with ANY ToolProvider, not just JDBC. It belongs in the core module.

**ToolsHandler:**
```java
public class ToolsHandler implements McpProtocolHandler {
    private final List<ToolProvider> toolProviders = new ArrayList<>();
    
    public void registerToolProvider(ToolProvider provider) {
        toolProviders.add(provider);
    }
    
    @Override
    public Set<String> getSupportedMethods() {
        return Set.of("tools/list", "tools/call");
    }
    
    // Aggregates tools from ALL providers for tools/list
    // Searches ALL providers to find tool for tools/call
}
```

### 4. Handler Implementations Updated

**InitializationHandler:**
```java
@Override
public Set<String> getSupportedMethods() {
    return Set.of("initialize");
}
```

**ToolsHandler (generic):**
```java
@Override
public Set<String> getSupportedMethods() {
    return Set.of("tools/list", "tools/call");
}
```

### 4. Tests Updated

- **McpServerTest**: Updated to use `getSupportedMethods()` instead of `canHandle()`
- **InitializationHandlerTest**: Tests verify supported methods set
- **New test**: `testDuplicateMethodRegistration()` verifies protection against duplicate method registration

## Performance Improvement

### Time Complexity

| Operation | Before | After |
|-----------|--------|-------|
| Handler registration | O(1) | O(m) where m = methods per handler |
| Request dispatch | O(n) where n = number of handlers | **O(1)** |

### Real-World Impact

For a server with:
- 10 handlers registered
- Each handling 1-2 methods
- Processing 1000 requests/second

**Before:** 10,000 `canHandle()` calls per second (10 × 1000)  
**After:** ~15 `getSupportedMethods()` calls total (one-time during startup)

**Performance gain:** ~99.85% reduction in handler checks

## Benefits

### 1. **Constant-Time Dispatch**
Request routing is now O(1) regardless of the number of handlers registered.

### 2. **Duplicate Method Detection**
The registration process now detects and prevents duplicate method registrations:
```java
if (methodHandlers.containsKey(method)) {
    throw new IllegalStateException("Method already registered");
}
```

### 3. **Clearer Contract**
Handlers explicitly declare their supported methods upfront rather than determining capability at runtime.

### 4. **Better Scalability**
Performance doesn't degrade as more handlers are added to the system.

### 5. **Simpler Logic**
No need to iterate through handlers for each request - direct HashMap lookup.

### Architecture: Two-Level O(1) Dispatch

**Level 1: Protocol-Level (O(1) HashMap)**
```
McpServer.methodHandlers: HashMap<String, McpProtocolHandler>
├─ "initialize" → InitializationHandler
├─ "tools/list" → ToolsHandler (generic, in core)
└─ "tools/call" → ToolsHandler (generic, in core)
```

**Level 2: Tool-Level (O(1) HashMap)**
```
ToolsHandler.toolIndex: HashMap<String, Tool>
├─ "jdbc_query" → QueryTool (from JdbcToolProvider)
├─ "jdbc_list_tables" → ListTablesTool (from JdbcToolProvider)
├─ "jdbc_describe_table" → DescribeTableTool (from JdbcToolProvider)
├─ "fs_read_file" → ReadFileTool (future FileSystemToolProvider)
├─ "fs_list_dir" → ListDirectoryTool (future FileSystemToolProvider)
└─ ... (all tools from all providers indexed by name)
```

**Complete Dispatch Flow:**
1. **Request arrives:** `{"method": "tools/call", "params": {"name": "jdbc_query", ...}}`
2. **Level 1 (O(1)):** `methodHandlers.get("tools/call")` → `ToolsHandler`
3. **Level 2 (O(1)):** `toolIndex.get("jdbc_query")` → `QueryTool`
4. **Execute:** `QueryTool.execute(params)`

**Performance:** Both dispatches are O(1), regardless of number of handlers or tools!

### Registration Flow
1. Handler calls `getSupportedMethods()` during registration
2. For each method, add mapping to HashMap
3. Check for duplicates and throw if method already registered
4. Handler is now ready to receive requests

### Dispatch Flow
1. Request arrives with method name
2. O(1) HashMap lookup: `methodHandlers.get(method)`
3. If found, delegate to handler
4. If not found, return METHOD_NOT_FOUND error

## Testing

**All 436 tests pass** ✓ (6 new tests added for ToolsHandler)

### New Tests - ToolsHandlerTest
1. `testGetSupportedMethods()` - Verifies handler supports `tools/list` and `tools/call`
2. `testListToolsWithNoProviders()` - Empty tool list works correctly
3. `testListToolsWithMultipleProviders()` - Aggregates tools from all providers
4. `testCallToolSuccess()` - O(1) lookup and execution works
5. `testCallUnknownTool()` - Unknown tools return proper error
6. `testDuplicateToolNameAcrossProviders()` - Duplicate tool names are detected and rejected

### Updated Tests
- `McpServerTest` - 6 tests (all passing) - Updated for getSupportedMethods()
- `InitializationHandlerTest` - 3 tests (all passing) - Updated for getSupportedMethods()

## Complexity Analysis

### Before
- Protocol dispatch: **O(n)** where n = number of handlers
- Tool dispatch: **O(p × t)** where p = number of providers, t = avg tools per provider
- **Total per request: O(n + p × t)** - Linear searches everywhere!

### After  
- Protocol dispatch: **O(1)** - HashMap lookup
- Tool dispatch: **O(1)** - HashMap lookup
- **Total per request: O(1)** - Constant time regardless of scale!

### Real-World Impact
For a server with 5 protocol handlers, 3 tool providers, and 20 tools total:

**Before:**
- 5 handler checks + up to 20 tool lookups = **up to 25 comparisons per request**

**After:**
- 1 HashMap lookup + 1 HashMap lookup = **2 HashMap lookups per request**

**Performance improvement:** ~92% reduction in lookup operations, plus the inherent efficiency of HashMap over linear search.

## Backward Compatibility

This is a **breaking change** to the `McpProtocolHandler` interface, but it only affects:
- Handler implementations (2 in codebase: InitializationHandler, JdbcToolsHandler)
- Unit tests

The change is internal to the protocol layer and doesn't affect:
- Tool implementations
- ToolProviders
- Client code
- Server configuration
- External APIs

## Migration Guide

To update custom handlers:

**Old:**
```java
@Override
public boolean canHandle(String method) {
    return method.equals("my_method");
}
```

**New:**
```java
@Override
public Set<String> getSupportedMethods() {
    return Set.of("my_method");
}
```

For multiple methods:
```java
@Override
public Set<String> getSupportedMethods() {
    return Set.of("method1", "method2", "method3");
}
```

---

*"Premature optimization is the root of all evil, but knowing your data structures is just good engineering."* - Adapted from Donald Knuth

