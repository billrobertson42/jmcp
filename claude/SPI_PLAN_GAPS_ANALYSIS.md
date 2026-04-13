# SPI Self-Assembly Plan: Gaps and Under-Specified Areas

**Date:** 2026-04-13  
**Scope:** Review of SPI_SELF_ASSEMBLY_PLAN.md against the actual codebase

---

## 1. `McpProvider` vs `ToolProvider` — Redundancy Left Unresolved

The plan introduces `McpProvider` with methods `getName()`, `initialize()`, `getTools()`, `shutdown()` — which are the *exact same four methods* on the existing `ToolProvider` interface, plus `getResourceProvider()`. The plan acknowledges this overlap (§4.3 table, §11 Q1/Q4) but waves it away by saying they have "different semantics."

They don't. The plan's own `JdbcMcpProvider` (§7 Step 5) is literally:

```java
@Override public void initialize() throws Exception { delegate.initialize(); }
@Override public List<Tool> getTools() { return delegate.getTools(); }
@Override public void shutdown() { delegate.shutdown(); }
```

And then `Main.java` immediately wraps every `McpProvider` back into a `ToolProvider` via `asToolProvider()`. So you have: `McpProvider` → (adapter) → `ToolProvider` → `ToolsHandler`. This is three layers for the price of one.

**What's missing:** A concrete decision — either:
- (a) Add `default ResourceProvider getResourceProvider()` to `ToolProvider` and use it as the SPI directly, or  
- (b) Replace `ToolProvider` with `McpProvider` throughout (including in `ToolsHandler`), or  
- (c) Keep both, but explain *specifically* why two nearly-identical interfaces with an adapter between them are worth the complexity.

The plan says "the assembler adapts McpProvider into ToolProvider for the ToolsHandler. This means ToolsHandler doesn't change at all." Not changing ToolsHandler is not a goal in itself — it's a one-line change to accept `McpProvider` there.

---

## 2. `exports org.peacetalk.jmcp.server.tools` — Jackson Serialization Impact

The plan says (§6.4): "exports org.peacetalk.jmcp.server.tools is removed... Verify that the export wasn't needed for Jackson reflective access; if so, use opens instead."

This is not just a footnote — it's a **hard requirement**. `ResourceProxyTool.execute()` returns result objects (`ListResourcesResult`, `ReadResourceResult`) that get serialized by `ToolsHandler` via `MAPPER.writeValueAsString(toolResult)`. Those result types are in `jmcp-core` (already open), so that direction is fine. But the return value from `execute()` is `Object`, and Jackson will serialize whatever concrete type comes back. If any result record lives in `org.peacetalk.jmcp.server.tools`, Jackson needs reflective access.

Looking at the actual code: `ResourceProxyTool.execute()` returns `response.result()` which is the deserialized result from `ResourcesHandler` — those are core model types. So the export is probably removable. But the plan flags it as "should be verified during implementation" instead of actually verifying it. It's verifiable *now* by reading the code. That said, the plan also misses the possibility that `opens` might be needed — Jackson 3 (tools.jackson) may need deep reflection depending on module configuration.

---

## 3. Error Handling During Provider Discovery is Sketchy

The plan's `Main.java` (§7 Step 6) has:

```java
ServiceLoader.load(McpProvider.class).forEach(providers::add);
for (McpProvider provider : providers) {
    provider.initialize();
}
```

**What's not addressed:**
- What happens if one provider's `initialize()` throws? Does it poison the entire server, or do you skip it and continue? The current hard-coded Main fails fast, which is appropriate for a single-provider setup. With N providers, the plan doesn't say.
- What if `ServiceLoader.load()` itself fails because a provider's constructor throws? `ServiceLoader` wraps that in `ServiceConfigurationError`. The plan doesn't handle this.
- What if a provider returns tools during `getTools()` but later its resources fail? There's no partial-registration or rollback concept.

---

## 4. The `asToolProvider` Adapter Creates a Lifecycle Anomaly

The plan's anonymous `ToolProvider` adapter:

```java
private static ToolProvider asToolProvider(McpProvider provider) {
    return new ToolProvider() {
        @Override public void initialize() {}  // already initialized
        @Override public void shutdown() {}    // lifecycle managed by McpProvider
        ...
    };
}
```

`ToolsHandler` stores a `List<ToolProvider>`. Nobody calls `initialize()` or `shutdown()` on those today — `Main.java` calls them on the concrete `JdbcToolProvider` directly. But nothing in the plan says this is safe because it was never formally part of the `ToolsHandler` contract either. If someone later adds lifecycle calls in `ToolsHandler` (not unreasonable), the no-op adapter silently eats them.

**What's missing:** A statement on whether `ToolsHandler` should or shouldn't manage lifecycle. If it shouldn't, remove `initialize()`/`shutdown()` from `ToolProvider` (they're already redundant with `McpProvider`). This circles back to gap #1.

---

## 5. `TransportProvider.priority()` Selection is Under-Specified

§4.1 says "When multiple transports are available, the highest priority is used unless overridden by command-line argument." §11 Q3 says "Recommended for Step 6. Parse args for --transport=stdio... This is a small addition."

It's not specified:
- What does `--transport=NAME` match against — `TransportProvider.getName()`?
- What if no transport matches the requested name — fail fast or fall back?
- What if two transports have the same priority — is it arbitrary? First-discovered?
- The plan says the CLI arg feature is "recommended" but the proposed `Main.java` code doesn't include it.

---

## 6. `run.sh` / Launch Configuration Impact Not Analyzed

The plan says (§10 Risk): "run.sh already uses glob for all JARs in dependency dir — should work."

This is probably true, but `run.sh` currently specifies:
```
--module org.peacetalk.jmcp.server/org.peacetalk.jmcp.server.Main
```

With ServiceLoader under JPMS, the provider modules must be on the **module path** (not classpath) and must be *resolved*. A module is only resolved if something `requires` it or if it's a root module. After the refactoring, `jmcp-server` no longer `requires` the provider modules. So `java --module-path ... --module org.peacetalk.jmcp.server/...Main` will **not** resolve `jmcp-jdbc` or `jmcp-transport-stdio` unless they're explicitly added as root modules.

You'd need either:
- `--add-modules org.peacetalk.jmcp.jdbc,org.peacetalk.jmcp.transport.stdio`
- Or `--add-modules ALL-MODULE-PATH`

**This is a correctness issue, not a risk.** The plan doesn't specify how to ensure the provider modules get resolved at runtime. Without this, ServiceLoader will return empty iterators and the server will start with zero providers and zero transports.

---

## 7. `JdbcMcpProvider` Constructor Requirements

ServiceLoader requires a public no-arg constructor on provider implementations. The plan's `JdbcMcpProvider` (§7 Step 5) does `new JdbcToolProvider()` in a field initializer, which calls the no-arg constructor. That's fine.

But the plan doesn't address: `JdbcToolProvider.initialize()` reads config from the filesystem and can throw `IOException`. If the config file doesn't exist and `JMCP_CONFIG` is unset, it falls back to an empty config with zero connections. That means `getTools()` returns 5 tool adapters that all share a `ConnectionManager` with no connections registered. Those tools will fail at runtime when called. The server will advertise tools that can't work.

**What's missing:** Whether the plan intends to change this behavior (e.g., skip registration if zero connections) or considers it acceptable. The current system has the same behavior, but with SPI discovery it becomes more likely someone runs the server without config (e.g., testing a new transport with no JDBC intent).

---

## 8. Server Test Module Becomes Incomplete

The server test `module-info.java` currently does:
```java
requires org.peacetalk.jmcp.server;
requires org.peacetalk.jmcp.core;
```

The plan's §9.2 says: "Remove requires org.peacetalk.jmcp.jdbc etc. if tests don't directly reference those modules." The tests (`ResourceProxyToolTest`, `ServerToolProviderTest`) use mocks and don't reference JDBC types, so this is fine.

But the plan proposes new tests (§9.3) including `ServerAssemblyTest` that tests `assembleServer()` with mock providers. Since `assembleServer` is `private static` in the plan's `Main.java`, the test can't call it without either:
- Making it package-private or extracting it to a testable class
- Using reflection (ugly in JPMS)

**What's missing:** How `assembleServer` is made testable. The plan waves at "Test assembleServer() with mock providers" without addressing visibility.

---

## 9. Logging — `requires org.slf4j` Removal

The plan says (§6.4): "requires org.slf4j is removed if no longer referenced."

The current `Main.java` doesn't use SLF4J — it uses `System.err.println` everywhere. But the plan doesn't verify whether anything else in the server module uses it. Looking at the actual module: only `Main.java` and the two tool classes exist, and none of them import SLF4J. So the removal is safe — but the plan is hedging ("if no longer referenced") instead of stating the fact.

---

## Summary: Severity Ranking

| # | Gap | Severity |
|---|-----|----------|
| 6 | Module resolution on the module path — ServiceLoader won't find providers | **Blocker** |
| 1 | McpProvider/ToolProvider redundancy unresolved | Design debt |
| 3 | Error handling during multi-provider discovery | Specification gap |
| 8 | `assembleServer` not testable as written | Specification gap |
| 5 | Transport selection semantics incomplete | Specification gap |
| 2 | Jackson serialization / exports verification deferred | Low risk but solvable now |
| 4 | Lifecycle adapter anomaly | Low risk |
| 7 | Zero-connection provider behavior with SPI | Behavioral question |
| 9 | SLF4J hedge | Trivial |

