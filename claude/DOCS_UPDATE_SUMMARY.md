# Docs Directory Update Summary

## Date: April 14, 2026

## What Changed

All 5 files in `/docs/` were significantly outdated (dated ~November 2025) and have been updated to reflect the current project state.

### Files Updated

| File | Change Scope | Key Updates |
|---|---|---|
| `ARCHITECTURE.md` | **Full rewrite** | SPI architecture, two-tier Tool interface, Resource system, updated key classes, correct configuration flow |
| `DEPENDENCY_GRAPH.md` | **Full rewrite** | Removed phantom `jmcp-driver-mgr` module, updated all diagrams, correct tools/results/resources, Jackson 3, Log4j2, JavaFX 25.0.1 |
| `MCP_CLIENT_GUI.md` | **Full rewrite** | Added 15 missing classes, resource navigation, communication logging, preferences, accessibility features |
| `COMPREHENSIVE_TEST_SUITE_SUMMARY.md` | **Full rewrite** | Expanded from SQL-validation-only to full project test suite coverage across all 5 modules |
| `SQL_VALIDATION_EXECUTIVE_SUMMARY.md` | **Targeted patches** | Fixed split file status, updated validation strategy, removed dead doc references |

### Major Discrepancies Found

1. **SPI Architecture not documented** — The docs described manual wiring. The actual architecture uses `McpProvider` and `TransportProvider` SPI contracts discovered via `ServiceLoader`, with zero compile-time coupling between server and providers.

2. **Tools completely wrong** — Docs listed `ListTablesTool`, `ListSchemasTool`, `DescribeTableTool`, `PreviewTableTool`. Actual tools: `QueryTool`, `ExplainQueryTool`, `GetRowCountTool`, `SampleDataTool`, `AnalyzeColumnTool`, plus `ResourceProxyTool`.

3. **Resource system missing** — Docs said "Future Enhancement". In reality, fully implemented with 14 resource classes, `ResourceProvider` interface, `ResourcesHandler`, `JdbcResourceProvider`, navigable URI hierarchy, and `ResourceProxyTool` bridge.

4. **Two-tier Tool interface not documented** — Core `Tool` interface with `execute(JsonNode)` vs JDBC `JdbcTool` with `execute(JsonNode, ConnectionContext)`, bridged by `JdbcToolAdapter`.

5. **Client module severely under-documented** — 4 classes listed vs 19 actual classes including service layer, UI components, resource navigation, preferences, and accessibility.

6. **External dependencies outdated** — Jackson shown as `com.fasterxml.jackson` (actually `tools.jackson` v3.x), SLF4J shown as primary logging (actually Log4j2), JavaFX shown as 23.0.1 (actually 25.0.1).

7. **`JdbcToolsHandler` referenced throughout** — This class no longer exists. It was replaced by `ToolsHandler` in the core module.

8. **McpServer dispatch pattern wrong** — Docs described `canHandle()` linear search. Actual uses `getSupportedMethods()` with `HashMap` O(1) dispatch.

