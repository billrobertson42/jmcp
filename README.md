# JDBC MCP Server

A Model Context Protocol (MCP) server that provides read-only access to JDBC-compatible databases.

It also provides a framework that allows easy addition of other tools.

## Key Ideas

* Safety 
  * Don't allow the database tools to make changes, AI makes mistakes, and sometimes you can't control permissions.
  * Minimal external dependencies - Reduce the CVE surface
* Extensibility
  * You should be able to add other tool/resource providers easily.
* Modern Java
  * Latest LTS JVM
  * Take advantage of its features

## Quick Start

There are a couple of basic starter scripts that should run the mcp after a maven build. You can easily see what it does by running the client.

### Build it

Requires Java 25 and a recent version of maven. 

```bash
mvn package
```

### Create Configuration File

See the Configuration section below for an example conifig file. 

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
- **Multiple Databases** - Connect to PostgreSQL, MySQL, Oracle, SQL Server, H2, SQLite
- **The "Semantic Firewall"** - JSqlParser validates that queries are in-fact queries, and therefore read-only, instead of relying on connection limits. Returning specific violations enables LLM self-correction for better success-per-prompt rates.
- **Dynamic Drivers** - JDBC drivers loaded on-demand from Maven Central
- **Modular Architecture** - Clean JPMS module structure utilizing SPI for extensible providers and zero compile-time dependencies.
- **Minimal Dependencies** - No Spring, minimal CVE surface
- **jlink Ready** - Build custom JVMs with only needed components

### Client

The client should be able to run any mcp that uses the stdio transport. It's extremely helpful for debugging and building understanding of the mcp protocol.

- **JavaFX GUI** - Modern, responsive interface
- **Tool Discovery** - Automatically list server tools
- **Dynamic Forms** - Auto-generated input forms from schemas
- **Pretty JSON** - Formatted result display
- **Any MCP Server** - Works with any MCP-compatible server that uses the stdio transport

## Modules

These are the JPMS modules currently included. If you wanted to add your own tools, you would create a module that is an mcp provider.

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
| **resource-proxy** | A crucial workaround for clients without MCP resource support (like some IDE Copilots) — exposes robust database resource navigation directly via the tools API |

## Available Resources

Resources provide navigable, HATEOAS-like access to database metadata. Start at `db://context` for a complete overview and follow the URIs to explore deeper.

| Resource URI Pattern | Description |
|------|-------------|
| `db://context` | Complete database overview — the recommended starting point |
| `db://connections` | List all configured connections |
| `db://connection/{id}` | Details for a specific connection |
| `db://connection/{id}/schemas` | List schemas for a connection |
| `db://connection/{id}/schema/{name}` | Schema details (tables, views, procedures) |
| `db://connection/{id}/schema/{name}/tables` | List tables in a schema |
| `db://connection/{id}/schema/{name}/table/{name}` | Table metadata (columns, indexes, FKs, constraints) |
| `db://connection/{id}/schema/{name}/views` | List views in a schema |
| `db://connection/{id}/schema/{name}/view/{name}` | View definition and metadata |
| `db://connection/{id}/schema/{name}/procedure/{name}` | Procedure/function signature and parameters |
| `db://connection/{id}/schema/{name}/relationships` | Schema-wide foreign key relationships |
| `db://connection/{id}/schema/{name}/table/{name}/relationships` | Foreign key relationships for a specific table |

> **Note:** Clients that don't support the MCP resources protocol (e.g., some IDE Copilots) can access all resources via the resource-proxy tool.

## "Human-in-the-Loop" Architecture & Testing

While this project accelerates development via AI assistance, its core value is the intentional, human-driven architecture. 
- **JPMS & SPI:** The server uses JPMS ServiceLoader (SPI) to discover transport and provider modules at
runtime. `jmcp-server` has no compile-time knowledge of specific tools or transports like `jmcp-jdbc` or
`jmcp-transport-stdio` — they are runtime dependencies only. 
- **Test Suite:** There are hundreds of test cases that the Semantic Firewall logic against basic DML/DDL rejection, complex bypass attempts, and dialect-specific edge cases to give users confidence.


## Configuration

The server reads a single JSON config file and routes each top-level section to the
corresponding provider by JPMS module name. Create file at its default location for a quick start: `~/.jmcp/config.json`:

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

### Fail-fast initialization

If a provider's `configure()` throws — missing config, bad credentials, unreachable
database — the server prints the full stack trace and exits immediately. It will not
start in a degraded state. Providers must throw if they cannot be configured correctly.

Providers may also use their own configuration mechanisms (system properties,
environment variables, etc.) in addition to the server-supplied config map.


## Requirements

- Java 25+
- Maven 3.9+
- Network access to Maven Central: JDBC drivers are downloaded as needed

## Security

The server enforces read-only access through:
1. **SQL Validation** - JSqlParser validates queries before execution, they must be read-only.
2. **Read-Only Connections** - HikariCP configured for read-only

Additionally, database users should have SELECT-only privileges. 

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [MCP Client GUI](docs/MCP_CLIENT_GUI.md)
- [SQL Validation](docs/SQL_VALIDATION_EXECUTIVE_SUMMARY.md)
- [Dependency Graph](docs/DEPENDENCY_GRAPH.md)
- [Test Documentation](docs/COMPREHENSIVE_TEST_SUITE_SUMMARY.md)

## License

This project is licensed under the [Apache License, Version 2.0](LICENSE).

