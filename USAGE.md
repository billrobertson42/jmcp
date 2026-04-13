# JDBC MCP Server - Usage Guide

## Quick Start

### 1. Build the Project

```bash
mvn clean package
```

### 2. Configure Database Connections

Create `~/.jmcp/config.json`. The top-level key is the JPMS module name of the
provider that consumes the configuration:

```json
{
  "org.peacetalk.jmcp.jdbc": {
    "default_id": "mydb",
    "expose_urls": false,
    "connections": [
      {
        "id": "mydb",
        "databaseType": "postgresql",
        "jdbcUrl": "jdbc:postgresql://localhost:5432/testdb",
        "username": "readonly_user",
        "password": "secret"
      }
    ]
  }
}
```

### 3. Run the Server

```bash
./run.sh
```

Or with Maven:

```bash
mvn -q -pl jmcp-server exec:java
```

The server will:
- Search for config: `-Djmcp.config` system property → `~/.jmcp/config.json` → `JMCP_CONFIG` env var
- Pass each provider its config section keyed by JPMS module name
- Crash with a full stack trace if any provider fails to initialize
- Download required JDBC drivers from Maven Central on first use
- Cache drivers in `~/.jmcp/drivers/`
- Start listening on stdin for JSON-RPC requests

## Configuration

### Database Types

Supported values for `databaseType`:

| Type | Driver | Maven Coordinates |
|------|--------|-------------------|
| `postgresql` | PostgreSQL | `org.postgresql:postgresql:42.7.1` |
| `mysql` | MySQL | `com.mysql:mysql-connector-j:8.3.0` |
| `mariadb` | MariaDB | `org.mariadb.jdbc:mariadb-java-client:3.3.2` |
| `oracle` | Oracle | `com.oracle.database.jdbc:ojdbc11:23.3.0.23.09` |
| `sqlserver` | SQL Server | `com.microsoft.sqlserver:mssql-jdbc:12.6.0.jre11` |
| `h2` | H2 | `com.h2database:h2:2.2.224` |
| `derby` | Derby | `org.apache.derby:derby:10.17.1.0` |
| `sqlite` | SQLite | `org.xerial:sqlite-jdbc:3.45.0.0` |

### Environment Variable Configuration

Instead of a config file, you can set `JMCP_CONFIG` to a JSON string containing the
full config (same module-keyed format):

```bash
export JMCP_CONFIG='{"org.peacetalk.jmcp.jdbc":{"default_id":"test","expose_urls":false,"connections":[{"id":"test","databaseType":"h2","jdbcUrl":"jdbc:h2:mem:test","username":"sa","password":""}]}}'
./run.sh
```

Or point to a specific file with a system property:

```bash
./run.sh -Djmcp.config=/path/to/my-config.json
```

## Available Tools

### 1. `query`

Execute SELECT queries with optional parameters.

**Input:**
```json
{
  "connectionId": "mydb",
  "sql": "SELECT * FROM users WHERE age > ?",
  "parameters": ["25"]
}
```

**Features:**
- Only SELECT queries allowed (DML/DDL rejected)
- Maximum 1000 rows returned
- 30-second query timeout
- Prepared statement support
- Set `validate_only` to `true` to check syntax without executing

### 2. `explain-query`

Get the execution plan for a SELECT query.

**Input:**
```json
{
  "connectionId": "mydb",
  "sql": "SELECT * FROM orders WHERE customer_id = 42"
}
```

### 3. `get-row-count`

Get the exact row count for a table.

**Input:**
```json
{
  "connectionId": "mydb",
  "table": "users",
  "schema": "public"
}
```

### 4. `sample-data`

Get sample rows from a table using a chosen strategy.

**Input:**
```json
{
  "connectionId": "mydb",
  "table": "users",
  "schema": "public",
  "strategy": "random",
  "sample_size": 20
}
```

Strategies: `first` (default), `random`, `last`. Maximum 100 rows.

### 5. `analyze-column`

Analyze a column's data distribution: distinct count, null count, min/max values,
and top N most-frequent values with frequencies.

**Input:**
```json
{
  "connectionId": "mydb",
  "table": "users",
  "column": "country",
  "schema": "public",
  "top_values": 10
}
```

### 6. `resource-proxy`

Workaround for MCP clients that don't implement the resources protocol (e.g., GitHub
Copilot). Exposes database schema resources via the tools API.

**Input:**
```json
{ "operation": "list" }
```
```json
{ "operation": "read", "uri": "db://context" }
```

Use `uri='db://context'` for a complete schema overview. Ignore this tool if your
client supports MCP resources natively.

## MCP Protocol Examples

### Initialize Connection

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "test-client",
      "version": "1.0"
    }
  }
}
```

### List Available Tools

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

### Execute a Tool

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get-row-count",
    "arguments": {
      "connectionId": "mydb",
      "table": "users"
    }
  }
}
```

## Integration with Claude Desktop

Add to your Claude Desktop configuration (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "jmcp": {
      "command": "/path/to/jmcp/run.sh"
    }
  }
}
```

Or specify the Java command directly:

```json
{
  "mcpServers": {
    "jmcp": {
      "command": "mvn",
      "args": [
        "-q",
        "-pl",
        "jmcp-server",
        "-f",
        "/path/to/jmcp/pom.xml",
        "exec:java"
      ]
    }
  }
}
```

## Security Best Practices

1. **Use Read-Only Database Accounts**: Create dedicated read-only users for MCP access
2. **Connection Pooling**: Limited to 5 connections per database
3. **Query Limits**: Maximum 1000 rows, 30-second timeout
4. **SQL Injection Prevention**: Only SELECT queries allowed, prepared statements used
5. **Isolated ClassLoaders**: Each JDBC driver runs in its own classloader

## Troubleshooting

### Driver Download Issues

If a driver fails to download from Maven Central, check:
- Internet connectivity
- Maven Central availability
- Driver coordinates are correct

The server will log driver downloads to stderr.

### Connection Issues

Check:
- JDBC URL format is correct
- Database is accessible from the server
- Credentials are valid
- Network/firewall allows connection

### ClassLoader Issues

Each database connection uses an isolated classloader for its JDBC driver. This prevents classpath conflicts between different driver versions.

## Development

### Adding New Tools

1. Create a class implementing `JdbcTool` in `jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/tools/`
2. Register the tool in `JdbcMcpProvider.initialize()` (add a `new JdbcToolAdapter(...)` line)
3. Rebuild: `mvn clean package`

### Adding New Transports

1. Create a new module (e.g., `jmcp-transport-sse`)
2. Implement `TransportProvider` from `jmcp-core`
3. Declare the provider in the module's `module-info.java`:
   ```java
   provides org.peacetalk.jmcp.core.transport.TransportProvider
       with com.example.SseTransportProvider;
   ```
4. Add the module as a `runtime` dependency of `jmcp-server`

The server discovers transports via `ServiceLoader` at startup and uses the one with
the highest `priority()`. No changes to `Main.java` are required.

### Adding New MCP Providers

1. Create a new module implementing `McpProvider` from `jmcp-core`
2. Declare the provider in `module-info.java`:
   ```java
   provides org.peacetalk.jmcp.core.McpProvider
       with com.example.MyMcpProvider;
   ```
3. Add the module as a `runtime` dependency of `jmcp-server`
4. Add a config section in `~/.jmcp/config.json` keyed by the module name

The provider's `initialize(Map<String, Object> config)` receives its config section.
Throw `IllegalStateException` or `IOException` if the config is missing or invalid —
the server will print the full stack trace and exit.

## Performance Considerations

- **Driver Caching**: Drivers are downloaded once and cached in `~/.jmcp/drivers/`
- **Connection Pooling**: HikariCP used for efficient connection management
- **Query Timeouts**: Prevent long-running queries from blocking
- **Row Limits**: Prevent memory exhaustion from large result sets

## Limitations

- Read-only operations only (no INSERT, UPDATE, DELETE, DDL)
- Maximum 1000 rows per query result
- 30-second query timeout
- Requires Java 25+ (for modules)
- LIMIT syntax may vary by database (standardized in tools)

