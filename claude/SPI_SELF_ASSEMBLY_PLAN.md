# JPMS SPI Self-Assembly Refactoring Plan

**Date:** 2026-03-30  
**Status:** Proposed  
**Scope:** Refactor jmcp-server from hard-coded wiring to runtime ServiceLoader discovery

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
11. [Open Questions](#open-questions)

---

## 1. Executive Summary <a id="executive-summary"></a>

The jmcp project is structured as five JPMS modules, but the server module (`jmcp-server`) defeats the purpose of modularization by hard-coding compile-time dependencies on every provider and transport. Adding a new transport (SSE, WebSocket) or a new domain provider (filesystem, git, etc.) currently requires modifying `Main.java`, updating `module-info.java`, and adding Maven dependencies — all in the server module.

This plan introduces two SPI contracts (`McpProvider` and `TransportProvider`) in `jmcp-core`, enabling the server to discover and assemble providers at runtime via `java.util.ServiceLoader`. The server module becomes a thin bootstrap shell with **zero** compile-time knowledge of specific providers or transports.

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
    // ...
    public ResourceProvider getResourceProvider() {  // NOT on the interface
        return resourceProvider;
    }
}
```

Main.java then calls this non-interface method directly:
```java
ResourceProvider resourceProvider = toolProvider.getResourceProvider(); // requires concrete type knowledge
```

**Impact:** If the server only knows `ToolProvider`, it cannot discover resources. The abstraction is leaky — the server must know the concrete class to get the full functionality.

#### Problem 3: Resource Proxy Tool Placement — Not a Problem

`ResourceProxyTool` and `ServerToolProvider` live in `jmcp-server`. This is **correct** and should stay as-is.

The proxy is created in exactly one place — the server bootstrap — via `new ServerToolProvider(resourcesHandler)`. It is not an SPI provider; it's not discovered via ServiceLoader. It's conditionally constructed by assembly code when resources exist. That makes it a server assembly concern, not a core abstraction.

While the proxy only depends on core types (`ResourcesHandler`, `Tool`, etc.), that alone doesn't justify relocation. Lots of code only depends on core types. The relevant questions are:

1. **Who creates it?** Only the server's assembly code. Moving it to core would force core to `exports` an implementation class solely for one consumer.
2. **Does it need to self-register via SPI?** No — it's created *differently* from discovered providers. It wraps the already-assembled `ResourcesHandler`, which is an artifact of assembly, not of discovery.
3. **Does it work with multiple providers?** Yes — it delegates to `ResourcesHandler`, which already aggregates all registered resource providers and routes by URI scheme. The proxy doesn't need to know how many providers exist. This works regardless of where the class lives.

The current `exports org.peacetalk.jmcp.server.tools` can be removed after the refactoring since no external module needs those types — the export was only needed because `jmcp-server` was itself the only consumer, and JPMS doesn't require exports for same-module access. (Actually, the export exists for Jackson serialization of result records in `ResourceProxyTool` — this should be verified during implementation.)

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

The server always claims it has resource and tool capabilities, regardless of whether any providers were actually discovered. If no `McpProvider` supplies resources, the server shouldn't advertise `resources` capability.

#### Problem 5: Configuration Is Embedded in the Provider

`JdbcToolProvider.loadConfiguration()` reads from `~/.jmcp/config.json` or `JMCP_CONFIG` env var. The configuration shape is JDBC-specific (`connections[]`, `default_id`, `expose_urls`). This is actually fine for self-contained providers, but there's no standardized way for the server to pass configuration sections to discovered providers.

#### Problem 6: `ConnectionPool` is a Private Inner Class Implementing a Public Interface

`ConnectionManager.ConnectionPool` is a `private static class` that implements `ConnectionContext` (a public interface). This is fine for encapsulation, but per the project's coding guidelines, inner classes that are used outside the class should be avoided. Currently `ConnectionPool` is only used inside `ConnectionManager`, so this is acceptable — but worth noting.

### 2.3 What's Actually Good

- **The core interfaces are clean.** `Tool`, `Resource`, `ToolProvider`, `ResourceProvider`, `McpTransport`, `McpRequestHandler` — these are well-designed and SPI-ready.
- **McpServer is a pure dispatcher.** It has no domain knowledge; it just routes methods to handlers.
- **The handler registration pattern works.** `McpProtocolHandler` → `getSupportedMethods()` → dispatch table is elegant and extensible.
- **The JdbcToolAdapter pattern is solid.** The adapter bridges `JdbcTool` (domain-specific with `ConnectionContext`) to `Tool` (generic). This separation of concerns should be preserved.
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
                    │  McpProvider           ◄──── SPI interface
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
         │ (implements        │   │  JdbcToolProvider        │
         │  TransportProvider)│   │  JdbcResourceProvider    │
         └────────────────────┘   │  ConnectionManager       │
                                  └──────────────────────────┘
```

### 3.2 Runtime Discovery Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Main.java (bootstrap)                        │
│                                                                     │
│  1. ServiceLoader.load(TransportProvider.class)                     │
│     └─► Discovers: StdioTransportProvider                           │
│         (future: SseTransportProvider, WebSocketTransportProvider)   │
│                                                                     │
│  2. ServiceLoader.load(McpProvider.class)                           │
│     └─► Discovers: JdbcMcpProvider                                  │
│         (future: FsMcpProvider, GitMcpProvider, etc.)                │
│                                                                     │
│  3. Assembly (in Main.java):                                        │
│     ├─► For each provider:                                          │
│     │   ├─ provider.initialize()                                    │
│     │   ├─ if provider.getTools() not empty → register ToolProvider │
│     │   └─ if provider.getResourceProvider() != null →              │
│     │       register ResourceProvider                               │
│     ├─► If any ResourceProvider found:                              │
│     │   └─ create ServerToolProvider (resource proxy bridge)        │
│     ├─► Configure InitializationHandler with actual capabilities    │
│     └─► Return assembled McpServer                                  │
│                                                                     │
│  4. transport.start(mcpServer)                                      │
│  5. Register shutdown hook (iterates all providers)                 │
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
     * When multiple transports are available, the highest priority is used
     * unless overridden by command-line argument.
     * Default: 0
     */
    default int priority() {
        return 0;
    }
}
```

### 4.2 `McpProvider` (new, in `jmcp-core`)

```java
package org.peacetalk.jmcp.core;

import java.util.Collections;
import java.util.List;

/**
 * SPI interface for MCP capability providers.
 * A provider can supply tools, resources, and (in future) prompts
 * from a single domain.
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

### 4.3 Why Not Just Extend `ToolProvider`?

The existing `ToolProvider` interface is fine as a lower-level contract used internally by `ToolsHandler`. `McpProvider` is a **higher-level SPI** that unifies the domain:

| Concern | `ToolProvider` | `McpProvider` |
|---------|---------------|---------------|
| Purpose | Feed tools to ToolsHandler | SPI entry point for a domain |
| Resources | Not supported | `getResourceProvider()` |
| Prompts (future) | Not supported | `getPromptProvider()` |
| Discovery | Manual registration | ServiceLoader |
| Lifecycle | Caller manages | Self-contained |

The assembler adapts `McpProvider` into `ToolProvider` for the `ToolsHandler`. This means **`ToolsHandler` doesn't change at all**.

---

## 5. What Moves Where <a id="what-moves-where"></a>

### 5.1 Classes Relocated

None. `ResourceProxyTool` and `ServerToolProvider` stay in `jmcp-server`. See §2.2 Problem 3 for rationale.

### 5.2 New Classes

| Class | Module | Package | Purpose |
|-------|--------|---------|---------|
| `TransportProvider` | `jmcp-core` | `o.p.j.core.transport` | SPI interface for transports |
| `McpProvider` | `jmcp-core` | `o.p.j.core` | SPI interface for domain providers |
| `StdioTransportProvider` | `jmcp-transport-stdio` | `o.p.j.transport.stdio` | SPI implementation |
| `JdbcMcpProvider` | `jmcp-jdbc` | `o.p.j.jdbc` | SPI implementation (wraps existing logic) |

### 5.3 Classes Modified

| Class | Module | Change |
|-------|--------|--------|
| `Main.java` | `jmcp-server` | Rewritten: ServiceLoader discovery + assembly (replaces hard-coded wiring) |
| `InitializationHandler` | `jmcp-core` | Accept dynamic capability configuration |
| `JdbcToolProvider` | `jmcp-jdbc` | Becomes internal to `JdbcMcpProvider` (or merged) |

### 5.4 Classes Deleted

None. `ServerToolProvider` and `ResourceProxyTool` remain in `jmcp-server` and continue to serve the same role — they're just created by the new assembly logic instead of the old hard-coded flow.

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

**After: No change.** The new `McpProvider` and `TransportProvider` interfaces live in packages that are already exported (`org.peacetalk.jmcp.core` and `org.peacetalk.jmcp.core.transport`). The `uses` clauses belong in the module that calls `ServiceLoader.load()` — that's `jmcp-server`, not `jmcp-core`.

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
    // ... qualified exports to test module ...
    // ... opens clauses ...
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
    // ... qualified exports to test module ...
    // ... opens clauses ...
    
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
}
```

Key changes:
- `requires org.peacetalk.jmcp.transport.stdio` and `requires org.peacetalk.jmcp.jdbc` are **removed** — these are now runtime-only dependencies discovered via ServiceLoader.
- `requires tools.jackson.databind` is kept — `ResourceProxyTool` uses Jackson directly.
- `exports org.peacetalk.jmcp.server.tools` is **removed** — no external module consumes those types. (Verify that the export wasn't needed for Jackson reflective access; if so, use `opens` instead.)
- `requires org.slf4j` is removed if no longer referenced.
- `uses` clauses are added — required by JPMS for any module calling `ServiceLoader.load()`.

---

## 7. Implementation Steps <a id="implementation-steps"></a>

### Step 1: Create `TransportProvider` interface in jmcp-core

**Files:** 
- New: `jmcp-core/src/main/java/org/peacetalk/jmcp/core/transport/TransportProvider.java`

Simple interface as defined in §4.1. Lives in the already-exported `org.peacetalk.jmcp.core.transport` package. No changes to `module-info.java` or existing classes.

**Verification:** Compiles. No runtime impact.

### Step 2: Create `McpProvider` interface in jmcp-core

**Files:**
- New: `jmcp-core/src/main/java/org/peacetalk/jmcp/core/McpProvider.java`

Interface as defined in §4.2. Lives in the already-exported `org.peacetalk.jmcp.core` package. No changes to `module-info.java` or existing classes.

**Verification:** Compiles. No runtime impact.

### Step 3: Make `InitializationHandler` capabilities dynamic

**Files:**
- Modify: `jmcp-core/.../protocol/InitializationHandler.java`

Add a constructor that accepts capability flags:

```java
public InitializationHandler(boolean hasTools, boolean hasResources) {
    this.hasTools = hasTools;
    this.hasResources = hasResources;
}
```

Adjust `handleInitialize()` to conditionally include capabilities:

```java
ServerCapabilities capabilities = new ServerCapabilities(
    null,
    null,
    null, // prompts — future
    hasResources ? new ServerCapabilities.ResourcesCapability(false, false) : null,
    hasTools ? new ServerCapabilities.ToolsCapability(false) : null
);
```

**Backward compatibility:** Keep the no-arg constructor with defaults (both true) so existing tests don't break until updated.

**Verification:** Existing tests pass with no-arg constructor. New test verifies dynamic behavior.

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
    
    @Override
    public int priority() { return 0; }  // default transport
}
```

**Verification:** ServiceLoader can find it in tests.

### Step 5: Implement `JdbcMcpProvider` in jmcp-jdbc

**Files:**
- New: `jmcp-jdbc/.../JdbcMcpProvider.java`
- Modify: `jmcp-jdbc/src/main/java/module-info.java` (add `provides`)

`JdbcMcpProvider` implements `McpProvider` and delegates to the existing `JdbcToolProvider` internally:

```java
package org.peacetalk.jmcp.jdbc;

import org.peacetalk.jmcp.core.*;
import java.util.List;

public class JdbcMcpProvider implements McpProvider {
    private final JdbcToolProvider delegate = new JdbcToolProvider();
    
    @Override
    public String getName() { return delegate.getName(); }
    
    @Override
    public void initialize() throws Exception { delegate.initialize(); }
    
    @Override
    public List<Tool> getTools() { return delegate.getTools(); }
    
    @Override
    public ResourceProvider getResourceProvider() { 
        return delegate.getResourceProvider(); 
    }
    
    @Override
    public void shutdown() { delegate.shutdown(); }
}
```

This is intentionally a thin wrapper. `JdbcToolProvider` continues to work as-is internally. Over time, the logic could be merged, but this avoids disrupting existing JDBC tests.

**Verification:** ServiceLoader can find it. Existing JDBC tests still pass.

### Step 6: Rewrite `Main.java` to use ServiceLoader + update server module-info

**Files:**
- Modify: `jmcp-server/.../Main.java`
- Modify: `jmcp-server/src/main/java/module-info.java` (as shown in §6.4)

This is the "flip the switch" step. Main.java takes on full responsibility for:
1. ServiceLoader discovery of transports and providers
2. Provider initialization
3. Assembly (wiring providers into `McpServer`, conditionally creating the resource proxy)
4. Starting the transport
5. Shutdown hook

New Main.java:

```java
package org.peacetalk.jmcp.server;

import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.ToolProvider;
import org.peacetalk.jmcp.core.protocol.*;
import org.peacetalk.jmcp.core.transport.McpTransport;
import org.peacetalk.jmcp.core.transport.TransportProvider;
import org.peacetalk.jmcp.server.tools.ServerToolProvider;

import java.util.*;

public class Main {
    static void main(String[] args) {
        List<McpProvider> providers = new ArrayList<>();
        McpTransport transport = null;

        try {
            // 1. Discover transport
            TransportProvider transportProvider = ServiceLoader.load(TransportProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .max(Comparator.comparingInt(TransportProvider::priority))
                .orElseThrow(() -> new RuntimeException(
                    "No TransportProvider found on module path"));

            System.err.println("Using transport: " + transportProvider.getName());

            // 2. Discover and initialize MCP providers
            ServiceLoader.load(McpProvider.class).forEach(providers::add);

            if (providers.isEmpty()) {
                System.err.println("WARNING: No McpProvider found on module path");
            }

            for (McpProvider provider : providers) {
                provider.initialize();
                System.err.println("Initialized provider: " + provider.getName());
            }

            // 3. Assemble server
            McpServer mcpServer = assembleServer(providers);

            // 4. Start transport
            transport = transportProvider.createTransport();

            final McpTransport finalTransport = transport;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.err.println("Shutting down...");
                try { finalTransport.stop(); } catch (Exception e) { /* log */ }
                providers.forEach(p -> {
                    try { p.shutdown(); } catch (Exception e) { /* log */ }
                });
            }));

            System.err.println("MCP Server starting...");
            transport.start(mcpServer);
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            // cleanup omitted for brevity — same pattern as current code
            System.exit(1);
        }
    }

    private static McpServer assembleServer(List<McpProvider> providers) {
        McpServer server = new McpServer();

        boolean hasTools = false;
        boolean hasResources = false;

        ToolsHandler toolsHandler = new ToolsHandler();
        ResourcesHandler resourcesHandler = new ResourcesHandler();

        for (McpProvider provider : providers) {
            List<Tool> tools = provider.getTools();
            if (!tools.isEmpty()) {
                hasTools = true;
                // Adapt McpProvider to ToolProvider for ToolsHandler
                toolsHandler.registerToolProvider(asToolProvider(provider));
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
            toolsHandler.registerToolProvider(proxyProvider);
            hasTools = true;
            System.err.println("Registered resource proxy tool for " +
                "resource-unaware clients");
        }

        server.registerHandler(new InitializationHandler(hasTools, hasResources));
        if (hasTools) server.registerHandler(toolsHandler);
        if (hasResources) server.registerHandler(resourcesHandler);

        return server;
    }

    /** Adapt an McpProvider to a ToolProvider for registration with ToolsHandler. */
    private static ToolProvider asToolProvider(McpProvider provider) {
        return new ToolProvider() {
            @Override public String getName() { return provider.getName(); }
            @Override public void initialize() {}  // already initialized
            @Override public List<Tool> getTools() { return provider.getTools(); }
            @Override public void shutdown() {}  // lifecycle managed by McpProvider
        };
    }
}
```

**Note:** No imports from `jmcp-jdbc` or `jmcp-transport-stdio`. The `asToolProvider` adapter is a simple anonymous implementation. If a second consumer of this pattern appears, it can be extracted to a named class; for now YAGNI.

**Assembly lives here because:**
- The server is the only place that does assembly. There's no second consumer.
- The resource proxy tool (`ServerToolProvider`) lives in this module — the assembly code is its only creator.
- Extracting to a shared `McpServerAssembler` in core is a future option if a test harness or embedded server needs it, but it would require either moving the proxy to core (which we've decided against) or splitting assembly across modules. Not worth it yet.

### Step 7: Update Maven POMs

**Files:**
- Modify: `jmcp-server/pom.xml`

Change jmcp-transport-stdio and jmcp-jdbc dependencies to `<scope>runtime</scope>`:

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
```

This ensures:
- They're on the module path at runtime (ServiceLoader needs them)
- They're NOT available at compile time (enforces the decoupling)

Remove `slf4j` dependency if no longer used in Main.java.

---

## 8. Configuration Strategy <a id="configuration-strategy"></a>

### Current State

Each provider loads its own config. `JdbcToolProvider` reads from:
1. System property `jdbc.mcp.config`
2. `~/.jmcp/config.json`
3. Environment variable `JMCP_CONFIG`

### Recommended Approach: Provider Self-Configuration (Phase 1)

Keep the current approach. Each `McpProvider` is responsible for finding its own configuration. This is the simplest path and avoids designing a configuration framework.

### Future Enhancement: Server-Mediated Configuration (Phase 2)

Add an optional method to `McpProvider`:

```java
default void configure(JsonNode providerConfig) {
    // Override to accept configuration from the server
}
```

The server would read a top-level config file with provider-specific sections:

```json
{
  "providers": {
    "jdbc": {
      "default_id": "default",
      "connections": [...]
    },
    "filesystem": {
      "root": "/data",
      "readonly": true
    }
  },
  "transport": "stdio"
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
| `jmcp-server` test `module-info.java` | Remove `requires org.peacetalk.jmcp.jdbc` etc. if tests don't directly reference those modules |
| Any test using `InitializationHandler()` no-arg | Still works (backward compatible) |

### 9.3 New Tests Needed

| Test | Module | Purpose |
|------|--------|---------|
| `InitializationHandlerDynamicTest` | `jmcp-core` | Verify capabilities reflect constructor args |
| `TransportProviderServiceLoaderTest` | `jmcp-transport-stdio` | Verify `StdioTransportProvider` is discoverable |
| `McpProviderServiceLoaderTest` | `jmcp-jdbc` | Verify `JdbcMcpProvider` is discoverable |
| `ServerAssemblyTest` | `jmcp-server` | Test `assembleServer()` with mock providers; verify proxy creation, capabilities |
| `SpiIntegrationTest` | `jmcp-server` | End-to-end: verify both SPIs discover their providers |

### 9.4 Tests Unaffected

All `jmcp-jdbc` tool and resource tests are unaffected — they test at the `JdbcTool` and `Resource` level, not through the SPI. The `JdbcToolProvider` continues to work as-is internally. All existing `ResourceProxyTool` and `ServerToolProvider` tests are unaffected — same classes, same location.

---

## 10. Risk Assessment <a id="risk-assessment"></a>

| Risk | Severity | Mitigation |
|------|----------|------------|
| ServiceLoader fails to discover providers due to missing `provides`/`uses` | High | Integration test in jmcp-server; run.sh smoke test |
| Module path misconfiguration in `run.sh` | Medium | run.sh already uses glob for all JARs in dependency dir — should work |
| Breaking change to `InitializationHandler` constructor | Low | Keep no-arg constructor as backward-compatible default |
| Configuration loading order changes | Low | `JdbcMcpProvider` delegates to same `JdbcToolProvider` logic |
| `asToolProvider` adapter in Main creates subtle behavior difference | Low | Adapter is trivial delegation; test with same inputs |

---

## 11. Open Questions <a id="open-questions"></a>

### Q1: Should `McpProvider` extend `ToolProvider`?

**Recommendation: No.** They serve different purposes. `ToolProvider` is an internal registration contract consumed by `ToolsHandler`. `McpProvider` is an SPI entry point. An adapter bridges them. This keeps `ToolsHandler` unchanged and avoids polluting the SPI interface with `ToolProvider`'s specific `initialize()`/`shutdown()` contract (which `McpProvider` also has, but with different semantics).

### Q2: What happens if zero `McpProvider` implementations are found?

The server should still start (a valid MCP server with no tools or resources). Log a warning. The `initialize` handshake will report no capabilities. This supports the case of a pure transport test.

### Q3: What about `--transport=NAME` CLI argument?

Recommended for Step 6. Parse `args` for `--transport=stdio` (or `--transport=sse`) and filter the discovered transports by name. If not specified, use highest priority. This is a small addition that provides important flexibility.

### Q4: Should `ToolProvider` be deprecated?

Not yet. It's still useful as the internal contract for `ToolsHandler`. Multiple `McpProvider`s may produce tools that need aggregation — the `ToolProvider` adapter layer handles that cleanly. Deprecation could happen in a future simplification pass.

### Q5: What about the `jmcp-client` module?

The client is a separate concern (JavaFX GUI that connects to an MCP server via subprocess). It does not participate in SPI discovery and is unaffected by this refactoring.

---

## Appendix: Step Execution Order Summary

```
Step  Description                                    Risk   Breaks Existing?
────  ──────────────────────────────────────────────  ─────  ────────────────
 1    Create TransportProvider interface              None   No
 2    Create McpProvider interface                    None   No
 3    Make InitializationHandler dynamic              Low    No (backward compat)
 4    Implement StdioTransportProvider                None   No
 5    Implement JdbcMcpProvider                       None   No
 6    Rewrite Main.java + server module-info          Med    Server assembly changes
 7    Update Maven POMs (runtime scope)               Low    Build-only change
```

Steps 1-3 can be done without affecting runtime behavior at all — they only add new types and a backward-compatible constructor. Steps 4-5 add SPI implementations alongside existing code. Step 6 is the "flip the switch" moment where the server moves from hard-coded to discovered assembly. Step 7 locks in the decoupling at the build level.

