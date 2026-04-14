# JDBC MCP Server

A Model Context Protocol (MCP) server that provides read-only access to JDBC-compatible databases.

## Quick Start

### Run the Server
```bash
./run.sh
```

### Run the GUI Client
```bash
./run-client.sh
```

## Features

### Server
- 🗄️ **Multiple Databases** - Connect to PostgreSQL, MySQL, Oracle, SQL Server, H2, Derby, SQLite
- 🔒 **Read-Only** - Enforced at multiple layers (SQL validation, connection configuration)
- 🔌 **Dynamic Drivers** - JDBC drivers loaded on-demand from Maven Central
- 🏗️ **Modular Architecture** - Clean JPMS module structure
- 🚀 **Zero Dependencies** - No Spring, minimal CVE surface
- 📦 **jlink Ready** - Build custom JVMs with only needed components

### Client
- 🖥️ **JavaFX GUI** - Modern, responsive interface
- 🛠️ **Tool Discovery** - Automatically list server tools
- 📝 **Dynamic Forms** - Auto-generated input forms from schemas
- 📄 **Pretty JSON** - Formatted result display
- 🔌 **Any MCP Server** - Works with any MCP-compatible server

## Modules

| Module | Purpose |
|--------|---------|
| **jmcp-core** | MCP protocol models and handlers |
| **jmcp-transport-stdio** | stdio transport implementation |
| **jmcp-jdbc** | JDBC tools, connection management, and driver loading |
| **jmcp-server** | Main server application |
| **jmcp-client** | JavaFX GUI client |

## Available Tools

| Tool | Description |
|------|-------------|
| **query** | Execute a read-only SELECT query (up to 1000 rows) |
| **explain-query** | Get the execution plan for a SELECT query |
| **get-row-count** | Get the exact row count for a table |
| **sample-data** | Get sample rows from a table (`first`, `random`, or `last`; max 100) |
| **analyze-column** | Analyze a column: distinct count, nulls, min/max, top values |
| **resource-proxy** | Workaround for clients without MCP resource support — exposes resources via the tools API |

## Architecture

```
┌─────────────┐         ┌──────────────┐         ┌──────────────┐
│   Client    │ stdio   │    Server    │  JDBC   │   Database   │
│   (GUI)     │◄───────►│  (MCP)       │◄───────►│  (Any JDBC)  │
└─────────────┘         └──────────────┘         └──────────────┘
```

The server uses JPMS ServiceLoader (SPI) to discover transport and provider modules at
runtime. `jmcp-server` has **zero compile-time knowledge** of `jmcp-jdbc` or
`jmcp-transport-stdio` — they are runtime dependencies only.

## Configuration

The server reads a single JSON config file and routes each top-level section to the
corresponding provider by JPMS module name. Create `~/.jmcp/config.json`:

```json
{
  "org.peacetalk.jmcp.jdbc": {
    "default_id": "mydb",
    "expose_urls": false,
    "connections": [
      {
        "id": "mydb",
        "databaseType": "postgresql",
        "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
        "username": "user",
        "password": "pass"
      }
    ]
  }
}
```

### Config file search order

1. System property: `-Djmcp.config=/path/to/config.json`
2. Default location: `~/.jmcp/config.json`
3. Environment variable: `JMCP_CONFIG` (JSON string, not a file path)

### Fail-fast initialization

If a provider's `configure()` throws — missing config, bad credentials, unreachable
database — the server prints the full stack trace and exits immediately. It will not
start in a degraded state.

Providers may also use their own configuration mechanisms (system properties,
environment variables, etc.) in addition to the server-supplied config map.

## Building

```bash
# Build all modules
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Build specific module
mvn clean package -pl jmcp-client -am
```

## Running

### Server
```bash
./run.sh
```

### Client
```bash
./run-client.sh
```

Or with Maven:
```bash
# Server
mvn -q -pl jmcp-server exec:java -Dexec.mainClass="org.peacetalk.jmcp.server.Main"

# Client
mvn -pl jmcp-client javafx:run
```

## Requirements

- Java 25+
- Maven 3.9+
- JDBC drivers (auto-downloaded as needed)

## Security

The server enforces read-only access through:
1. **SQL Validation** - JSqlParser validates queries before execution
2. **Read-Only Connections** - HikariCP configured for read-only
3. **Connection-Level** - Database users should have SELECT-only privileges

## Documentation

- [Architecture](claude/ARCHITECTURE.md)
- [MCP Client GUI](claude/MCP_CLIENT_GUI.md)
- [SQL Validation](claude/SQL_VALIDATION_EXECUTIVE_SUMMARY.md)
- [Dependency Graph](claude/DEPENDENCY_GRAPH.md)
- [Test Documentation](claude/COMPREHENSIVE_TEST_SUITE_SUMMARY.md)

## License

Apache 2.0 (or your chosen license)

## Contributing

This is a reference implementation. Feel free to fork and adapt for your needs.

