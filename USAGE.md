# JDBC MCP Server - Usage Guide

## Quick Start

### 1. Build the Project

```bash
mvn clean package
```

### 2. Configure Database Connections

Create `~/.jmcp/config.json`:

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
        "password": "secret",
        "schemaFilter": ["public", "reporting"]
      }
    ]
  }
}
```

### Connection fields

| Field | Required | Description |
|-------|----------|-------------|
| `id` | yes      | Unique identifier for this connection |
| `databaseType` | yes      | Database type — see supported types below |
| `jdbcUrl` | yes      | JDBC connection URL |
| `username` | yes      | Database username |
| `password` | yes      | Database password |
| `schemaFilter` | no       | JSON array of schema names to expose. When omitted, all schemas are visible. Recommended for large databases with many schemas. |


The server will:
- Search for config: `-Djmcp.config` system property → `~/.jmcp/config.json` → `JMCP_CONFIG` env var
- Pass each provider its config section keyed by JPMS module name
- Read configuration from `~/.jmcp/config.json` or `jmcp_CONFIG` environment variable

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

Instead of a config file, you can use the `jmcp_CONFIG` environment variable:

## Available Tools

### 1. `query`

export jmcp_CONFIG='{"connections":[{"id":"test","databaseType":"h2","jdbcUrl":"jdbc:h2:mem:test","username":"sa","password":""}]}'

**Input:**
```json
{
  "connectionId": "mydb",
  "sql": "SELECT * FROM users WHERE age > ?",
  "parameters": ["25"]
}
```

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

- Only SELECT queries allowed
```json
{
  "connectionId": "mydb",
  "table": "users",
  "schema": "public"
}
```

### 4. `sample-data`


**Input:**
```json
{
  "connectionId": "mydb",
  "table": "users",
### 2. `list-tables`
List all tables in the database or specific schema.
  "sample_size": 20
}
```

  "schema": "public"

### 5. `analyze-column`

### 3. `list-schemas`
List all schemas/catalogs in the database.

**Input:**
```json
{
  "connectionId": "mydb"
}
```

### 4. `describe-table`

### 5. `get-row-count`

**Input:**
```json
{
  "table": "users",
  "column": "country",
  "schema": "public",
  "top_values": 10
}
```

  "schema": "public"
### 6. `preview-table`

**Input:**
```json
{ "operation": "list" }
```
```json
{ "operation": "read", "uri": "db://context" }
```
Get the first N rows from a table (default 10, max 100).

## MCP Protocol Examples

### Initialize Connection

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
  "limit": 20
      "name": "test-client",
      "version": "1.0"
    }
  }
}
```


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

If your environment requires an HTTP proxy, configure it via system properties or
environment variables — no changes to `config.json` are needed.

**Java system properties** (add to the `java` command in `run.sh`):
```
-Dhttp.proxyHost=proxy.example.com
-Dhttp.proxyPort=8080
```

**Environment variables** (upper- or lower-case are both accepted):
```bash
export HTTP_PROXY=http://proxy.example.com:8080
# or
export http_proxy=http://proxy.example.com:8080
```

Resolution order: system properties take precedence over environment variables.
`HTTP_PROXY` is checked before `HTTPS_PROXY`.


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
2. Register the tool in `JdbcMcpProvider.configure()` (add a `new JdbcToolAdapter(...)` line)
3. Rebuild: `mvn clean package`

### Adding New Transport
### Adding New Transports

Before we do this, I think we need to address the single-threaded
issue.

The server discovers transports via `ServiceLoader` at startup and uses the one with
the highest `priority()`. No changes to `Main.java` are required.

stdio transport should be the highest priority as coming up in that
mode for an inexperienced user is the safest thing to do.

### Adding New MCP Providers

1. Create a module implementing `McpTransport`
2. Register in `Main.java` as alternative to stdio
3. Common transports: SSE (Server-Sent Events), WebSocket, HTTP
1. Create a new module implementing `McpProvider` from `jmcp-core`
2. Declare the provider in `module-info.java`:
   ```java
   provides org.peacetalk.jmcp.core.McpProvider
       with com.example.MyMcpProvider;
   ```
3. Add the module as a `runtime` dependency of `jmcp-server`
4. Add a config section in `~/.jmcp/config.json` keyed by the module name

The provider's `configure(Map<String, Object> config)` receives its config section.
Throw `IllegalStateException` or `IOException` if the config is missing or invalid —
2. Register the tool in `Main.java`

## Performance Considerations

- **Driver Caching**: Drivers are downloaded once and cached in `~/.jmcp/drivers/`
- **Connection Pooling**: HikariCP used for efficient connection management
- **Query Timeouts**: Prevent long-running queries from blocking
- **Row Limits**: Prevent memory exhaustion from large result sets

## Features

- 30-second query timeout
- Requires Java 25+ (for modules)
- SAFETY! Read-only operations only (no INSERT, UPDATE, DELETE, DDL)

## Limitations

- Maximum 1000 rows per query result
- LIMIT syntax may vary by database (standardized in tools)
