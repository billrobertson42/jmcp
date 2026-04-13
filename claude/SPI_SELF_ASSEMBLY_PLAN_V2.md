# JPMS SPI Self-Assembly Refactoring Plan (v2)

**Date:** 2026-04-13  
**Status:** Proposed  
**Supersedes:** SPI_SELF_ASSEMBLY_PLAN.md (2026-03-30)  
**Scope:** Refactor jmcp-server from hard-coded wiring to runtime ServiceLoader discovery

---

## Changes from v1

This revision incorporates decisions from the [gaps analysis](SPI_PLAN_GAPS_ANALYSIS.md):

1. **`ToolProvider` is replaced by `McpProvider` throughout**, including inside `ToolsHandler`. No adapter layer, no redundant interface.
2. **`org.peacetalk.jmcp.server.tools` uses `opens`** (not `exports`) for Jackson serialization safety.
3. **Fail-fast error handling** at every stage of discovery and initialization. Any failure crashes the server with full diagnostics.
4. **The `asToolProvider` adapter is eliminated** — direct consequence of replacing `ToolProvider` with `McpProvider`.
5. **Transport selection details** deferred to future transport work. Only stdio exists today.
6. **`run.sh` updated with `--add-modules ALL-MODULE-PATH`** to ensure ServiceLoader can resolve provider modules. Eventual jlink deployment will handle this differently.
7. **Provider initialization must fail hard** if configuration is missing or broken. The server must not start in a broken state.
8. **Assembly logic extracted to a testable static method** on `Main`, with a qualified export so the test module can call it.
9. **`requires org.slf4j` is removed.** No class in jmcp-server references SLF4J.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Critical Analysis of Current Architecture](#critical-analysis)
3. [Target Architecture](#target-architecture)
4. [SPI Interface Definitions](#spi-interface-definitions)
5. [What Moves Where](#what-moves-where)
6. [Module Declarations (Before/After)](#module-declarations)
7. [Implementation Steps](#implementation-steps)
8. [Configuration Strategy](#configuration-strategy)
9. [Test Impact](#test-impact)
10. [Risk Assessment](#risk-assessment)

---

## 1. Executive Summary <a id="executive-summary"></a>

The jmcp project is structured as five JPMS modules, but the server module (`jmcp-server`) defeats the purpose of modularization by hard-coding compile-time dependencies on every provider and transport. Adding a new transport (SSE, WebSocket) or a new domain provider (filesystem, git, etc.) currently requires modifying `Main.java`, updating `module-info.java`, and adding Maven dependencies — all in the server module.

This plan introduces two SPI contracts (`McpProvider` and `TransportProvider`) in `jmcp-core`, enabling the server to discover and assemble providers at runtime via `java.util.ServiceLoader`. The server module becomes a thin bootstrap shell with **zero** compile-time knowledge of specific providers or transports.

`McpProvider` **replaces** the existing `ToolProvider` interface. The two interfaces are nearly identical (`getName()`, `initialize()`, `getTools()`, `shutdown()`), differing only in that `McpProvider` adds `getResourceProvider()`. Maintaining both would create a redundant adapter layer with no benefit. `ToolsHandler` is updated to accept `McpProvider` directly.

---

## 2. Critical Analysis of Current Architecture <a id="critical-analysis"></a>

### 2.1 Current Module Dependency Graph

```
                    ┌──────────────────┐
                    │   jmcp-server    │
                    │   (Main.java)    │
                    └──┬──────┬──────┬─┘
          compile-time │      │      │ compile-time
          dependency   │      │      │ dependency
                       ▼      │      ▼
  ┌────────────────────┐      │    ┌───────────────────────┐
  │ jmcp-transport-    │      │    │     jmcp-jdbc         │
  │ stdio              │      │    │ (JdbcToolProvider,    │
  │ (StdioTransport)   │      │    │  JdbcResourceProvider)│
  └────────┬───────────┘      │    └──────────┬────────────┘
           │                  │               │
           │                  ▼               │
           │          ┌──────────────┐        │
           └─────────►│  jmcp-core   │◄───────┘
                      │ (interfaces) │
                      └──────────────┘
```

### 2.2 Problems Identified

#### Problem 1: Hard-Coded Assembly in Main.java

```java
// Main.java — every provider is a direct instantiation
JdbcToolProvider toolProvider = new JdbcToolProvider();  // hard-coded
StdioTransport transport = new StdioTransport();        // hard-coded
ServerToolProvider serverToolProvider = new ServerToolProvider(resourcesHandler); // hard-coded
```

The server's `module-info.java` declares:
```java
requires org.peacetalk.jmcp.transport.stdio;  // compile-time coupling
requires org.peacetalk.jmcp.jdbc;             // compile-time coupling
```

**Impact:** Adding any new provider or transport requires editing three files in jmcp-server (Main.java, module-info.java, pom.xml). This is the opposite of a plugin architecture.

#### Problem 2: `ToolProvider` and `ResourceProvider` Are Disconnected

The `ToolProvider` interface has no concept of resources:

```java
public interface ToolProvider {
    void initialize() throws Exception;
    List<Tool> getTools();
    void shutdown();
    String getName();
    // ← no getResourceProvider()
}
```

But `JdbcToolProvider` adds its own `getResourceProvider()` method outside the interface:

```java
public class JdbcToolProvider implements ToolProvider {
    public ResourceProvider getResourceProvider() {  // NOT on the interface
        return resourceProvider;
    }
}
```

Main.java then calls this non-interface method directly:
```java
ResourceProvider resourceProvider = toolProvider.getResourceProvider(); // requires concrete type knowledge
```

**Resolution (v2):** `ToolProvider` is replaced by `McpProvider`, which includes `getResourceProvider()` as a default method. This eliminates the leak entirely.

#### Problem 3: Resource Proxy Tool Placement — Not a Problem

`ResourceProxyTool` and `ServerToolProvider` live in `jmcp-server`. This is **correct** and stays as-is. The proxy is created in exactly one place — the server bootstrap — via `new ServerToolProvider(resourcesHandler)`. It wraps the already-assembled `ResourcesHandler`, which is an artifact of assembly, not of discovery.

The `exports org.peacetalk.jmcp.server.tools` directive exists for Jackson serialization. Examination of `ResourceProxyTool.execute()` shows it returns `response.result()` from `ResourcesHandler.handle()` — these are core model types (`ListResourcesResult`, `ReadResourceResult`) in `org.peacetalk.jmcp.core.model`, which is already opened. The server.tools package does not contain serialized types. However, to guard against future changes and satisfy Jackson's reflective access pattern, the directive is **changed from `exports` to `opens`**.

#### Problem 4: InitializationHandler Hard-Codes Capabilities

```java
ServerCapabilities capabilities = new ServerCapabilities(
    null,  // experimental
    null,  // logging
    null,  // prompts
    new ServerCapabilities.ResourcesCapability(false, false),  // always advertises resources
    new ServerCapabilities.ToolsCapability(false)               // always advertises tools
);
```

The server always claims it has resource and tool capabilities, regardless of whether any providers were actually discovered.

#### Problem 5: Configuration Is Embedded in the Provider

`JdbcToolProvider.loadConfiguration()` reads from `~/.jmcp/config.json` or `JMCP_CONFIG` env var. This is fine for self-contained providers. Each `McpProvider` is responsible for finding its own configuration.

However, the current fallback to an empty configuration with zero connections is a problem. The server should not start with tools that cannot function. See §7 Step 5.

### 2.3 What's Actually Good

- **The core interfaces are clean.** `Tool`, `Resource`, `ResourceProvider`, `McpTransport`, `McpRequestHandler` — well-designed and SPI-ready.
- **McpServer is a pure dispatcher.** It has no domain knowledge; it just routes methods to handlers.
- **The handler registration pattern works.** `McpProtocolHandler` → `getSupportedMethods()` → dispatch table is elegant and extensible.
- **The JdbcToolAdapter pattern is solid.** The adapter bridges `JdbcTool` (domain-specific with `ConnectionContext`) to `Tool` (generic). This separation of concerns is preserved.
- **JDBC driver classloader isolation is well-implemented.** `JdbcDriverManager` + `DriverClassLoader` is independent of the SPI concern and doesn't need to change.

---

## 3. Target Architecture <a id="target-architecture"></a>

### 3.1 Target Module Dependency Graph

```
                       ┌─────────────────────────┐
                       │      jmcp-server        │
                       │                         │
                       │  Main.java (bootstrap   │
                       │    + assembly logic)    │
                       │  ServerToolProvider     │
                       │  ResourceProxyTool      │
                       └────────┬────────────────┘
                                │ compile-time (only jmcp-core)
                                │
                    ┌───────────▼────────────┐
                    │      jmcp-core         │
                    │                        │
                    │  TransportProvider     ◄──── SPI interface
                    │  McpProvider           ◄──── SPI interface (replaces ToolProvider)
                    │  McpTransport          │
                    │  McpServer             │
                    │  ToolsHandler          │
                    │  ResourcesHandler      │
                    │  InitializationHandler │
                    └────────────────────────┘
                          ▲              ▲
           runtime only   │              │  runtime only
           (ServiceLoader)│              │  (ServiceLoader)
                          │              │
         ┌────────────────┴───┐   ┌──────┴───────────────────┐
         │ jmcp-transport-    │   │      jmcp-jdbc           │
         │ stdio              │   │                          │
         │                    │   │  JdbcMcpProvider         │
         │ StdioTransport     │   │  (implements McpProvider)│
         │ Provider           │   │                          │
         │ (implements        │   │  JdbcResourceProvider    │
         │  TransportProvider)│   │  ConnectionManager       │
         └────────────────────┘   └──────────────────────────┘
```

### 3.2 Runtime Discovery Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Main.java (bootstrap)                        │
│                                                                     │
│  1. ServiceLoader.load(TransportProvider.class)                     │
│     └─► Discovers: StdioTransportProvider                           │
│     └─► If none found → crash with diagnostic message               │
│                                                                     │
│  2. ServiceLoader.load(McpProvider.class)                           │
│     └─► Discovers: JdbcMcpProvider                                  │
│     └─► If none found → crash (server has no purpose without        │
│         providers)                                                   │
│                                                                     │
│  3. Initialize all providers:                                        │
│     └─► provider.initialize() — if ANY throws → crash with full     │
│         stack trace and provider name                                │
│                                                                     │
│  4. Assembly (in Main.assembleServer()):                             │
│     ├─► For each provider:                                          │
│     │   ├─ Register with ToolsHandler (it reads getTools())         │
│     │   └─ if provider.getResourceProvider() != null →              │
│     │       register ResourceProvider                               │
│     ├─► If any ResourceProvider found:                              │
│     │   └─ create ServerToolProvider (resource proxy bridge)        │
│     ├─► Configure InitializationHandler with actual capabilities    │
│     └─► Return assembled McpServer                                  │
│                                                                     │
│  5. transport.start(mcpServer)                                      │
│  6. Register shutdown hook (iterates all providers)                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. SPI Interface Definitions <a id="spi-interface-definitions"></a>

### 4.1 `TransportProvider` (new, in `jmcp-core`)

```java
package org.peacetalk.jmcp.core.transport;

/**
 * SPI interface for MCP transport providers.
 * Implementations are discovered via java.util.ServiceLoader.
 *
 * To register a transport provider, add to module-info.java:
 *   provides org.peacetalk.jmcp.core.transport.TransportProvider
 *       with com.example.MyTransportProvider;
 */
public interface TransportProvider {
    
    /** Human-readable name (e.g., "stdio", "sse", "websocket") */
    String getName();
    
    /** Create a new transport instance */
    McpTransport createTransport();
    
    /**
     * Priority for transport selection. Higher values = higher priority.
     * When multiple transports are available, the highest priority is used.
     * Default: 0
     */
    default int priority() {
        return 0;
    }
}
```

Transport selection semantics (CLI override, tie-breaking) are deferred to future transport work. Only stdio exists today.

### 4.2 `McpProvider` (replaces `ToolProvider`, in `jmcp-core`)

```java
package org.peacetalk.jmcp.core;

import java.util.Collections;
import java.util.List;

/**
 * SPI interface for MCP capability providers.
 * A provider can supply tools, resources, and (in future) prompts
 * from a single domain.
 *
 * This interface replaces the former ToolProvider interface and serves
 * as both the SPI entry point for ServiceLoader discovery and the
 * internal registration contract for ToolsHandler.
 *
 * Implementations are discovered via java.util.ServiceLoader.
 *
 * To register a provider, add to module-info.java:
 *   provides org.peacetalk.jmcp.core.McpProvider
 *       with com.example.MyMcpProvider;
 */
public interface McpProvider {
    
    /** Human-readable name for this provider */
    String getName();
    
    /**
     * Initialize the provider. Called once after discovery.
     * Providers should load their own configuration.
     *
     * Implementations MUST throw if initialization cannot complete
     * successfully. A provider that initializes without error is
     * expected to be fully functional. Do not swallow errors or
     * fall back to a degraded state silently.
     */
    void initialize() throws Exception;
    
    /**
     * Get all tools from this provider.
     * Returns empty list if this provider has no tools.
     */
    default List<Tool> getTools() {
        return Collections.emptyList();
    }
    
    /**
     * Get the resource provider, if this provider supports resources.
     * Returns null if this provider has no resources.
     */
    default ResourceProvider getResourceProvider() {
        return null;
    }
    
    // Future: default PromptProvider getPromptProvider() { return null; }
    
    /** Clean up resources used by this provider */
    void shutdown();
}
```

### 4.3 Why `McpProvider` Replaces `ToolProvider`

`ToolProvider` has four methods: `getName()`, `initialize()`, `getTools()`, `shutdown()`. `McpProvider` has the same four, plus `getResourceProvider()`. Keeping both creates:

1. A `JdbcMcpProvider` wrapper that delegates every call to `JdbcToolProvider` — pure boilerplate.
2. An `asToolProvider()` adapter in Main that wraps `McpProvider` back into `ToolProvider` for `ToolsHandler` — another boilerplate layer.
3. A lifecycle anomaly where the adapter's `initialize()`/`shutdown()` are no-ops because lifecycle is "managed by McpProvider" — but nothing enforces this.

Replacing `ToolProvider` with `McpProvider` eliminates all three. `ToolsHandler` accepts `McpProvider` directly. `JdbcToolProvider` becomes `JdbcMcpProvider` by changing its `implements` clause and is used both as the SPI provider and as the `ToolsHandler` registration unit.

---

## 5. What Moves Where <a id="what-moves-where"></a>

### 5.1 Classes Relocated

None. `ResourceProxyTool` and `ServerToolProvider` stay in `jmcp-server`.

### 5.2 New Classes

| Class | Module | Package | Purpose |
|-------|--------|---------|---------|
| `TransportProvider` | `jmcp-core` | `o.p.j.core.transport` | SPI interface for transports |
| `McpProvider` | `jmcp-core` | `o.p.j.core` | SPI interface, replaces `ToolProvider` |
| `StdioTransportProvider` | `jmcp-transport-stdio` | `o.p.j.transport.stdio` | SPI implementation |

### 5.3 Classes Modified

| Class | Module | Change |
|-------|--------|--------|
| `Main.java` | `jmcp-server` | Rewritten: ServiceLoader discovery + assembly (replaces hard-coded wiring) |
| `InitializationHandler` | `jmcp-core` | Accept dynamic capability configuration |
| `ToolsHandler` | `jmcp-core` | Accept `McpProvider` instead of `ToolProvider` |
| `JdbcToolProvider` | `jmcp-jdbc` | Renamed to `JdbcMcpProvider`, implements `McpProvider` instead of `ToolProvider` |
| `ServerToolProvider` | `jmcp-server` | Implements `McpProvider` instead of `ToolProvider` |

### 5.4 Classes Deleted

| Class | Module | Reason |
|-------|--------|--------|
| `ToolProvider` | `jmcp-core` | Replaced by `McpProvider` |

---

## 6. Module Declarations (Before/After) <a id="module-declarations"></a>

### 6.1 `jmcp-core` module-info.java

**Before:**
```java
module org.peacetalk.jmcp.core {
    requires tools.jackson.databind;
    requires tools.jackson.core;
    requires jakarta.validation;
    requires org.hibernate.validator;

    exports org.peacetalk.jmcp.core.model;
    exports org.peacetalk.jmcp.core.protocol;
    exports org.peacetalk.jmcp.core.schema;
    exports org.peacetalk.jmcp.core.transport;
    exports org.peacetalk.jmcp.core.validation;
    exports org.peacetalk.jmcp.core;

    opens org.peacetalk.jmcp.core.schema;
    opens org.peacetalk.jmcp.core.protocol;
    opens org.peacetalk.jmcp.core.model;
}
```

**After: No change.** `McpProvider` lives in `org.peacetalk.jmcp.core` (already exported). `TransportProvider` lives in `org.peacetalk.jmcp.core.transport` (already exported). `ToolProvider.java` is deleted from the same package. The `uses` clauses belong in the module that calls `ServiceLoader.load()` — that's `jmcp-server`, not `jmcp-core`.

### 6.2 `jmcp-transport-stdio` module-info.java

**Before:**
```java
module org.peacetalk.jmcp.transport.stdio {
    requires org.peacetalk.jmcp.core;
    exports org.peacetalk.jmcp.transport.stdio;
}
```

**After:**
```java
module org.peacetalk.jmcp.transport.stdio {
    requires org.peacetalk.jmcp.core;
    exports org.peacetalk.jmcp.transport.stdio;
    
    provides org.peacetalk.jmcp.core.transport.TransportProvider
        with org.peacetalk.jmcp.transport.stdio.StdioTransportProvider;
}
```

### 6.3 `jmcp-jdbc` module-info.java

**Before:**
```java
module org.peacetalk.jmcp.jdbc {
    requires org.peacetalk.jmcp.core;
    requires java.sql;
    requires tools.jackson.databind;
    requires jdbctl;
    requires net.sf.jsqlparser;

    exports org.peacetalk.jmcp.jdbc;
    exports org.peacetalk.jmcp.jdbc.config to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.driver to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.tools to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.tools.results to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.validation to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.resources to org.peacetalk.jmcp.jdbc.test;

    opens org.peacetalk.jmcp.jdbc.config;
    opens org.peacetalk.jmcp.jdbc.tools.results;
    opens org.peacetalk.jmcp.jdbc.resources;
}
```

**After:**
```java
module org.peacetalk.jmcp.jdbc {
    requires org.peacetalk.jmcp.core;
    requires java.sql;
    requires tools.jackson.databind;
    requires jdbctl;
    requires net.sf.jsqlparser;

    exports org.peacetalk.jmcp.jdbc;
    exports org.peacetalk.jmcp.jdbc.config to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.driver to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.tools to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.tools.results to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.validation to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.resources to org.peacetalk.jmcp.jdbc.test;

    opens org.peacetalk.jmcp.jdbc.config;
    opens org.peacetalk.jmcp.jdbc.tools.results;
    opens org.peacetalk.jmcp.jdbc.resources;
    
    provides org.peacetalk.jmcp.core.McpProvider
        with org.peacetalk.jmcp.jdbc.JdbcMcpProvider;
}
```

### 6.4 `jmcp-server` module-info.java

**Before:**
```java
module org.peacetalk.jmcp.server {
    requires org.peacetalk.jmcp.core;
    requires org.peacetalk.jmcp.transport.stdio;
    requires org.peacetalk.jmcp.jdbc;
    requires org.slf4j;
    requires tools.jackson.databind;

    exports org.peacetalk.jmcp.server.tools;
}
```

**After:**
```java
module org.peacetalk.jmcp.server {
    requires org.peacetalk.jmcp.core;
    requires tools.jackson.databind;

    // SPI consumption — server discovers providers at runtime
    uses org.peacetalk.jmcp.core.McpProvider;
    uses org.peacetalk.jmcp.core.transport.TransportProvider;

    // Jackson reflective access for tool result serialization
    opens org.peacetalk.jmcp.server.tools;

    // Assembly logic accessible to test module
    exports org.peacetalk.jmcp.server to org.peacetalk.jmcp.server.test;
}
```

Key changes:
- `requires org.peacetalk.jmcp.transport.stdio` — **removed** (runtime-only, discovered via ServiceLoader).
- `requires org.peacetalk.jmcp.jdbc` — **removed** (runtime-only, discovered via ServiceLoader).
- `requires org.slf4j` — **removed**. No class in jmcp-server references SLF4J. `Main.java` uses `System.err.println`, and neither `ServerToolProvider` nor `ResourceProxyTool` import SLF4J.
- `exports org.peacetalk.jmcp.server.tools` — **changed to `opens`**. No external module needs compile-time access to these types, but Jackson may need reflective access for serialization. Using `opens` is the correct JPMS directive for this.
- `exports org.peacetalk.jmcp.server to org.peacetalk.jmcp.server.test` — **added**. Qualified export so the test module can call `Main.assembleServer()` for the `ServerAssemblyTest`.
- `uses` clauses — **added**. Required by JPMS for any module calling `ServiceLoader.load()`.

---

## 7. Implementation Steps <a id="implementation-steps"></a>

### Step 1: Create `TransportProvider` interface in jmcp-core

**Files:** 
- New: `jmcp-core/src/main/java/org/peacetalk/jmcp/core/transport/TransportProvider.java`

Simple interface as defined in §4.1. Lives in the already-exported `org.peacetalk.jmcp.core.transport` package. No changes to `module-info.java` or existing classes.

**Verification:** Compiles. No runtime impact.

### Step 2: Create `McpProvider` interface and delete `ToolProvider`

**Files:**
- New: `jmcp-core/src/main/java/org/peacetalk/jmcp/core/McpProvider.java`
- Delete: `jmcp-core/src/main/java/org/peacetalk/jmcp/core/ToolProvider.java`

Interface as defined in §4.2. Lives in the already-exported `org.peacetalk.jmcp.core` package. No changes to `module-info.java`.

Deleting `ToolProvider.java` will cause compilation failures in `ToolsHandler`, `JdbcToolProvider`, and `ServerToolProvider`. These are fixed in Steps 3, 5, and 6 respectively.

**Verification:** `jmcp-core` compiles after Step 3. Other modules compile after their respective steps.

### Step 3: Update `ToolsHandler` to accept `McpProvider` + make `InitializationHandler` dynamic

**Files:**
- Modify: `jmcp-core/.../protocol/ToolsHandler.java`
- Modify: `jmcp-core/.../protocol/InitializationHandler.java`

#### ToolsHandler changes

Replace all references to `ToolProvider` with `McpProvider`:

```java
import org.peacetalk.jmcp.core.McpProvider;

public class ToolsHandler implements McpProtocolHandler {
    private final List<McpProvider> providers;
    private final Map<String, Tool> toolIndex;

    public void registerProvider(McpProvider provider) {
        providers.add(provider);
        for (Tool tool : provider.getTools()) {
            String toolName = tool.getName();
            if (toolIndex.containsKey(toolName)) {
                throw new IllegalStateException(
                    "Tool '" + toolName + "' is already registered. " +
                    "Tool names must be unique across all providers."
                );
            }
            toolIndex.put(toolName, tool);
        }
    }
    
    // handleListTools iterates providers calling getTools() — same logic
    // handleCallTool uses toolIndex — same logic
}
```

The method is renamed from `registerToolProvider` to `registerProvider` since `McpProvider` is not tool-specific.

#### InitializationHandler changes

Add a constructor that accepts capability flags:

```java
private final boolean hasTools;
private final boolean hasResources;

public InitializationHandler(boolean hasTools, boolean hasResources) {
    this.hasTools = hasTools;
    this.hasResources = hasResources;
}

/** Backward-compatible default: advertise both capabilities. */
public InitializationHandler() {
    this(true, true);
}
```

Adjust `handleInitialize()`:

```java
ServerCapabilities capabilities = new ServerCapabilities(
    null,
    null,
    null, // prompts — future
    hasResources ? new ServerCapabilities.ResourcesCapability(false, false) : null,
    hasTools ? new ServerCapabilities.ToolsCapability(false) : null
);
```

**Verification:** All existing jmcp-core tests pass. `InitializationHandler` no-arg constructor preserves backward compatibility.

### Step 4: Implement `StdioTransportProvider` in jmcp-transport-stdio

**Files:**
- New: `jmcp-transport-stdio/.../StdioTransportProvider.java`
- Modify: `jmcp-transport-stdio/src/main/java/module-info.java` (add `provides`)

```java
package org.peacetalk.jmcp.transport.stdio;

import org.peacetalk.jmcp.core.transport.McpTransport;
import org.peacetalk.jmcp.core.transport.TransportProvider;

public class StdioTransportProvider implements TransportProvider {
    @Override
    public String getName() { return "stdio"; }
    
    @Override
    public McpTransport createTransport() { return new StdioTransport(); }
}
```

**Verification:** ServiceLoader can find it in tests.

### Step 5: Rename `JdbcToolProvider` to `JdbcMcpProvider`, implement `McpProvider`, enforce configuration

**Files:**
- Rename: `JdbcToolProvider.java` → `JdbcMcpProvider.java`
- Modify: `jmcp-jdbc/src/main/java/module-info.java` (add `provides`)
- Update: all references within jmcp-jdbc that import `JdbcToolProvider`

`JdbcMcpProvider` implements `McpProvider` directly. It already has all the required methods. The change is:

```java
// Was:
public class JdbcToolProvider implements ToolProvider {
// Becomes:
public class JdbcMcpProvider implements McpProvider {
```

The `getResourceProvider()` method already exists and now satisfies the `McpProvider` interface directly — no wrapper class needed.

#### Configuration must fail hard

The current `loadConfiguration()` falls back to an empty config with zero connections when no config file or environment variable is found. This means the server starts with 5 tools that all fail at runtime because `ConnectionManager` has no connections registered.

**Change:** If no configuration source is found, `loadConfiguration()` must throw an exception:

```java
private static JdbcConfiguration loadConfiguration() throws IOException {
    // Try system property first (for testing)
    String configProperty = System.getProperty("jdbc.mcp.config");
    if (configProperty != null) {
        Path configPath = Paths.get(configProperty);
        if (Files.exists(configPath)) {
            System.err.println("Loading configuration from system property: " + configPath);
            JsonNode configNode = MAPPER.readTree(configPath.toFile());
            return MAPPER.treeToValue(configNode, JdbcConfiguration.class);
        }
        throw new IOException(
            "Configuration file specified by system property 'jdbc.mcp.config' " +
            "does not exist: " + configPath);
    }

    // Try config file
    Path configPath = Paths.get(System.getProperty("user.home"), ".jmcp", "config.json");
    if (Files.exists(configPath)) {
        System.err.println("Loading configuration from: " + configPath);
        JsonNode configNode = MAPPER.readTree(configPath.toFile());
        return MAPPER.treeToValue(configNode, JdbcConfiguration.class);
    }

    // Try environment variable
    String configEnv = System.getenv("JMCP_CONFIG");
    if (configEnv != null) {
        System.err.println("Loading configuration from JMCP_CONFIG environment variable");
        JsonNode configNode = MAPPER.readTree(configEnv);
        return MAPPER.treeToValue(configNode, JdbcConfiguration.class);
    }

    // No configuration found — fail hard
    throw new IOException(
        "No JDBC configuration found. Provide one of:\n" +
        "  1. System property: -Djdbc.mcp.config=/path/to/config.json\n" +
        "  2. Config file: " + configPath + "\n" +
        "  3. Environment variable: JMCP_CONFIG='{...}'\n" +
        "See config.example.json for the expected format.");
}
```

Similarly, if a config is found but contains zero connections, `initialize()` should throw:

```java
if (jdbcConfig.connections().length == 0) {
    throw new IllegalStateException(
        "JDBC configuration contains no connections. " +
        "At least one connection must be configured.");
}
```

This ensures the server never starts with broken tools. Since the project philosophy is fail fast and fail hard, and this is the initialization step, the exception propagates up to `Main.java` and crashes the server with a full stack trace.

**Verification:** ServiceLoader can find `JdbcMcpProvider`. Existing JDBC tests that supply config via system property continue to pass. A new test verifies the missing-config exception.

### Step 6: Rewrite `Main.java` + update server module-info + update `ServerToolProvider`

**Files:**
- Modify: `jmcp-server/.../Main.java`
- Modify: `jmcp-server/.../tools/ServerToolProvider.java`
- Modify: `jmcp-server/src/main/java/module-info.java` (as shown in §6.4)

#### ServerToolProvider changes

`ServerToolProvider` implements `McpProvider` instead of `ToolProvider`:

```java
public class ServerToolProvider implements McpProvider {
    // getName(), initialize(), getTools(), shutdown() — unchanged
    // getResourceProvider() — inherits default (returns null), which is correct
}
```

#### Main.java rewrite

`Main.java` is the "flip the switch" step. It takes on full responsibility for:
1. ServiceLoader discovery of transports and providers
2. Provider initialization (fail-fast on any error)
3. Assembly (wiring providers into `McpServer`, conditionally creating the resource proxy)
4. Starting the transport
5. Shutdown hook

`assembleServer` is a **public static method** so the test module can call it via the qualified export of `org.peacetalk.jmcp.server`:

```java
package org.peacetalk.jmcp.server;

import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.protocol.*;
import org.peacetalk.jmcp.core.transport.McpTransport;
import org.peacetalk.jmcp.core.transport.TransportProvider;
import org.peacetalk.jmcp.server.tools.ServerToolProvider;

import java.util.*;

public class Main {

    public static void main(String[] args) {
        List<McpProvider> providers = new ArrayList<>();
        McpTransport transport = null;

        try {
            // 1. Discover transport
            TransportProvider transportProvider = ServiceLoader.load(TransportProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .max(Comparator.comparingInt(TransportProvider::priority))
                .orElseThrow(() -> new RuntimeException(
                    "No TransportProvider found on module path. " +
                    "Ensure a transport module (e.g., jmcp-transport-stdio) " +
                    "is on the module path and resolved via --add-modules."));

            System.err.println("Using transport: " + transportProvider.getName());

            // 2. Discover MCP providers
            ServiceLoader.load(McpProvider.class).forEach(providers::add);

            if (providers.isEmpty()) {
                throw new RuntimeException(
                    "No McpProvider found on module path. " +
                    "Ensure at least one provider module (e.g., jmcp-jdbc) " +
                    "is on the module path and resolved via --add-modules.");
            }

            // 3. Initialize all providers — fail fast on any error
            for (McpProvider provider : providers) {
                System.err.println("Initializing provider: " + provider.getName() + "...");
                provider.initialize();  // throws on failure → crashes server
                System.err.println("Initialized provider: " + provider.getName());
            }

            // 4. Assemble server
            McpServer mcpServer = assembleServer(providers);

            // 5. Start transport
            transport = transportProvider.createTransport();

            final McpTransport finalTransport = transport;
            final List<McpProvider> finalProviders = List.copyOf(providers);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.err.println("Shutting down...");
                try {
                    finalTransport.stop();
                } catch (Exception e) {
                    System.err.println("Error stopping transport: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
                for (McpProvider p : finalProviders) {
                    try {
                        p.shutdown();
                    } catch (Exception e) {
                        System.err.println("Error shutting down provider " +
                            p.getName() + ": " + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }
            }));

            System.err.println("MCP Server starting...");
            transport.start(mcpServer);
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);

            // Clean up any providers that were successfully initialized
            for (McpProvider p : providers) {
                try {
                    p.shutdown();
                } catch (Exception ex) {
                    System.err.println("Error during cleanup of " +
                        p.getName() + ": " + ex.getMessage());
                    ex.printStackTrace(System.err);
                }
            }
            if (transport != null) {
                try {
                    transport.stop();
                } catch (Exception ex) {
                    System.err.println("Error stopping transport: " + ex.getMessage());
                    ex.printStackTrace(System.err);
                }
            }

            System.exit(1);
        }
    }

    /**
     * Assemble an McpServer from discovered and initialized providers.
     * Public for testability (accessible via qualified export to test module).
     *
     * @param providers initialized McpProvider instances
     * @return a fully assembled McpServer ready for transport.start()
     */
    public static McpServer assembleServer(List<McpProvider> providers) {
        McpServer server = new McpServer();

        boolean hasTools = false;
        boolean hasResources = false;

        ToolsHandler toolsHandler = new ToolsHandler();
        ResourcesHandler resourcesHandler = new ResourcesHandler();

        for (McpProvider provider : providers) {
            if (!provider.getTools().isEmpty()) {
                hasTools = true;
                toolsHandler.registerProvider(provider);
            }

            ResourceProvider rp = provider.getResourceProvider();
            if (rp != null) {
                hasResources = true;
                resourcesHandler.registerResourceProvider(rp);
            }
        }

        // Create resource proxy bridge if any resources were found
        if (hasResources) {
            ServerToolProvider proxyProvider = new ServerToolProvider(resourcesHandler);
            toolsHandler.registerProvider(proxyProvider);
            hasTools = true;
            System.err.println("Registered resource proxy tool for " +
                "resource-unaware clients");
        }

        server.registerHandler(new InitializationHandler(hasTools, hasResources));
        if (hasTools) server.registerHandler(toolsHandler);
        if (hasResources) server.registerHandler(resourcesHandler);

        return server;
    }
}
```

**Key differences from v1:**
- No `ToolProvider` import, no `asToolProvider()` adapter.
- `assembleServer` is `public static` (not `private static`).
- Zero providers = crash, not warning. The server has no purpose without providers.
- Full cleanup in the catch block with per-provider error reporting.
- Error messages in `orElseThrow` include diagnostic hints about `--add-modules`.

### Step 7: Update Maven POMs and `run.sh`

**Files:**
- Modify: `jmcp-server/pom.xml`
- Modify: `run.sh`

#### pom.xml changes

Change jmcp-transport-stdio and jmcp-jdbc dependencies to `<scope>runtime</scope>`. Remove `slf4j-api` dependency:

```xml
<dependency>
    <groupId>org.peacetalk</groupId>
    <artifactId>jmcp-transport-stdio</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.peacetalk</groupId>
    <artifactId>jmcp-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- slf4j-api dependency REMOVED -->
```

This ensures:
- They're on the module path at runtime (ServiceLoader needs them)
- They're NOT available at compile time (enforces the decoupling)

#### run.sh changes

JPMS only resolves modules that are transitively `requires`-ed from the root module or explicitly listed as root modules. After removing `requires org.peacetalk.jmcp.jdbc` and `requires org.peacetalk.jmcp.transport.stdio` from the server module, those modules will be on the module path but **not resolved**. `ServiceLoader.load()` will return empty iterators for unresolved modules.

**Fix:** Add `--add-modules ALL-MODULE-PATH` to force resolution of all modules on the module path:

```bash
java --module-path "$MODULE_PATH" $JVM_ARGS \
     --add-modules ALL-MODULE-PATH \
     --module org.peacetalk.jmcp.server/org.peacetalk.jmcp.server.Main
```

`ALL-MODULE-PATH` resolves every module found on the module path, which is exactly what we want for SPI discovery. This is the standard JPMS pattern for ServiceLoader-based plugin systems.

**Note:** When the project moves to jlink deployment, `--add-modules` will be specified at jlink time instead, and the resulting custom runtime image will have all modules pre-resolved. The `run.sh` approach is for development/packaging use.

---

## 8. Configuration Strategy <a id="configuration-strategy"></a>

### Current State

Each provider loads its own config. `JdbcMcpProvider` reads from:
1. System property `jdbc.mcp.config`
2. `~/.jmcp/config.json`
3. Environment variable `JMCP_CONFIG`

If none are found, initialization **throws** (changed from v1 — see Step 5).

### Recommended Approach: Provider Self-Configuration (Phase 1)

Keep the current approach. Each `McpProvider` is responsible for finding its own configuration. This is the simplest path and avoids designing a configuration framework.

### Future Enhancement: Server-Mediated Configuration (Phase 2)

Add an optional method to `McpProvider`:

```java
default void configure(JsonNode providerConfig) {
    // Override to accept configuration from the server
}
```

This is **not needed for the initial refactoring** but the `McpProvider` interface is designed to accommodate it later.

---

## 9. Test Impact <a id="test-impact"></a>

### 9.1 Tests That Must Move

None. `ResourceProxyTool` and `ServerToolProvider` stay in `jmcp-server`, so their tests stay too.

### 9.2 Tests That Must Be Updated

| Test | Change |
|------|--------|
| `ServerToolProviderTest` | Change `ToolProvider`-related assertions if any (current tests don't reference `ToolProvider` by name, so this is likely just a recompile) |
| `jmcp-server` test `module-info.java` | No changes needed — it already only `requires org.peacetalk.jmcp.server` and `org.peacetalk.jmcp.core` |
| `jmcp-jdbc` tests referencing `JdbcToolProvider` | Update to reference `JdbcMcpProvider` (class rename) |
| Any test using `ToolsHandler.registerToolProvider()` | Update to `registerProvider()` |
| Any test using `InitializationHandler()` no-arg | Still works (backward compatible) |

### 9.3 New Tests Needed

| Test | Module | Purpose |
|------|--------|---------|
| `InitializationHandlerDynamicTest` | `jmcp-core` | Verify capabilities reflect constructor args: tools-only, resources-only, both, neither |
| `TransportProviderServiceLoaderTest` | `jmcp-transport-stdio` | Verify `StdioTransportProvider` is discoverable via ServiceLoader |
| `McpProviderServiceLoaderTest` | `jmcp-jdbc` | Verify `JdbcMcpProvider` is discoverable via ServiceLoader |
| `JdbcMcpProviderMissingConfigTest` | `jmcp-jdbc` | Verify that `initialize()` throws with diagnostic message when no config is found |
| `JdbcMcpProviderEmptyConnectionsTest` | `jmcp-jdbc` | Verify that `initialize()` throws when config has zero connections |
| `ServerAssemblyTest` | `jmcp-server` | Call `Main.assembleServer()` with mock `McpProvider`s; verify proxy creation, capability flags, handler registration |
| `SpiIntegrationTest` | `jmcp-server` | End-to-end: verify both SPIs discover their providers when modules are on the module path |

#### Testability of `assembleServer`

`Main.assembleServer()` is `public static`. The server module declares:
```java
exports org.peacetalk.jmcp.server to org.peacetalk.jmcp.server.test;
```

This qualified export makes `Main` visible to the test module. `ServerAssemblyTest` can create mock `McpProvider` implementations (using Mockito or simple anonymous implementations) and call `Main.assembleServer(List.of(mockProvider))` directly.

### 9.4 Tests Unaffected

All `jmcp-jdbc` tool and resource tests that operate at the `JdbcTool` and `Resource` level are unaffected — they don't go through the SPI. All existing `ResourceProxyTool` tests are unaffected — same class, same location, same behavior.

---

## 10. Risk Assessment <a id="risk-assessment"></a>

| Risk | Severity | Mitigation |
|------|----------|------------|
| ServiceLoader fails to discover providers due to missing `provides`/`uses` | High | Integration test in jmcp-server; run.sh smoke test |
| Provider modules not resolved without `--add-modules` | **Blocker** | `--add-modules ALL-MODULE-PATH` in run.sh (§7 Step 7); verified in SpiIntegrationTest |
| `ToolProvider` deletion breaks downstream code | Medium | Compile-time error — all references updated in Steps 3, 5, 6. Search-and-replace across codebase. |
| `JdbcToolProvider` rename breaks JDBC tests | Medium | Search-and-replace within jmcp-jdbc module. Tests use the class directly, not via SPI. |
| Missing config now crashes instead of starting empty | Medium | Intentional behavior change. New tests verify the error messages are diagnostic. Existing tests supply config via system property and are unaffected. |
| Breaking change to `InitializationHandler` constructor | Low | No-arg constructor preserved as backward-compatible default. |
| `opens org.peacetalk.jmcp.server.tools` insufficient for Jackson | Low | Current code only serializes core model types. The `opens` is defensive; can be removed if verified unnecessary. |

---

## Appendix: Step Execution Order Summary

```
Step  Description                                    Risk   Breaks Existing?
────  ──────────────────────────────────────────────  ─────  ────────────────
 1    Create TransportProvider interface              None   No
 2    Create McpProvider + delete ToolProvider        Med    Yes (compile errors)
 3    Update ToolsHandler + InitializationHandler     Med    Fixes compile errors from Step 2
 4    Implement StdioTransportProvider                None   No
 5    Rename JdbcToolProvider → JdbcMcpProvider       Med    JDBC tests need update
 6    Rewrite Main.java + ServerToolProvider + mod    Med    Server assembly changes
 7    Update POMs + run.sh                            Low    Build/launch changes
```

Steps 1-3 must be done together (they form an atomic change to jmcp-core). Step 4 is independent. Steps 5-6 can be done in either order but both must be done before Step 7. Step 7 locks in the decoupling at the build and launch level.

**Recommended execution:** Steps 1-3 as one commit, Step 4 as one commit, Step 5 as one commit, Steps 6-7 as one commit.

