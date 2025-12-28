# Configuration Loading Refactoring

**Date:** December 28, 2025

## Summary

Moved configuration loading from the `Main` class into the `JdbcToolProvider.initialize()` method, making the server completely generic and decoupled from JDBC-specific configuration details.

## Changes Made

### JdbcToolProvider Enhanced

**File:** `jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/JdbcToolProvider.java`

Added `loadConfiguration()` private static method that:
- Tries to load from `~/.jmcp/config.json` file
- Falls back to `JMCP_CONFIG` environment variable
- Uses default empty configuration if neither exists

The `initialize(Object configuration)` method now:
- Accepts `null` as a valid parameter
- When `null`, calls `loadConfiguration()` to load its own config
- When non-null, expects a `JdbcConfiguration` instance (for testing/custom scenarios)

### Main Class Further Simplified

**File:** `jmcp-server/src/main/java/org/peacetalk/jmcp/server/Main.java`

**Before:**
```java
// Load configuration
JdbcConfiguration config = loadConfiguration();

// Initialize JDBC tool provider
toolProvider = new JdbcToolProvider();
toolProvider.initialize(config);
```

**After:**
```java
// Initialize JDBC tool provider (it will load its own configuration)
toolProvider = new JdbcToolProvider();
toolProvider.initialize(null);
```

**Removed from Main:**
- `ObjectMapper MAPPER` field
- `loadConfiguration()` method
- All Jackson imports
- All JDBC configuration class imports
- All file I/O for configuration

## Benefits

### 1. **True Separation of Concerns**
The server module now has **zero knowledge** of how JDBC tools are configured. It simply creates a provider and tells it to initialize itself.

### 2. **Minimal Dependencies**
Main.java now only imports:
- `org.peacetalk.jmcp.core.ToolProvider`
- `org.peacetalk.jmcp.core.protocol.*`
- `org.peacetalk.jmcp.jdbc.JdbcToolProvider`
- `org.peacetalk.jmcp.jdbc.JdbcToolsHandler`
- `org.peacetalk.jmcp.transport.stdio.StdioTransport`

No Jackson, no file I/O, no configuration models.

### 3. **Provider Autonomy**
Each ToolProvider is now fully autonomous:
- Knows where to find its configuration
- Knows how to parse its configuration
- Handles all initialization internally

### 4. **Easier Extension**
Adding a new tool provider is even simpler now - Main doesn't need to know anything about configuration:

```java
// Hypothetical file system tool provider
ToolProvider fsProvider = new FileSystemToolProvider();
fsProvider.initialize(null);  // It knows how to configure itself
```

### 5. **Better Testability**
Can still pass explicit configuration for testing:

```java
// In tests
JdbcConfiguration testConfig = new JdbcConfiguration(...);
provider.initialize(testConfig);
```

## Code Structure

```
jmcp-server/Main.java
├── Creates JdbcToolProvider
├── Calls provider.initialize(null)
└── Provider handles everything else

jmcp-jdbc/JdbcToolProvider
├── initialize(null) → loadConfiguration()
├── loadConfiguration() 
│   ├── Try ~/.jmcp/config.json
│   ├── Try JMCP_CONFIG env var
│   └── Use defaults
└── Continue with initialization
```

## Testing

All 429 tests continue to pass. The refactoring is completely backward compatible.

## Migration Impact

**None.** The refactoring is internal. The server still:
- Loads from `~/.jmcp/config.json` by default
- Falls back to `JMCP_CONFIG` environment variable
- Works with default empty configuration
- Behaves identically to before

The only difference is **where** the configuration loading logic lives - now properly encapsulated in the JDBC provider.

---

*"Perfection is achieved, not when there is nothing more to add, but when there is nothing more to take away."* - Antoine de Saint-Exupéry

