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
- Read configuration from `~/.jmcp/config.json` or `jmcp_CONFIG` environment variable
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

Instead of a config file, you can use the `jmcp_CONFIG` environment variable:

```bash
export jmcp_CONFIG='{"connections":[{"id":"test","databaseType":"h2","jdbcUrl":"jdbc:h2:mem:test","username":"sa","password":""}]}'
./run.sh
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
- Only SELECT queries allowed
- Maximum 1000 rows returned
- 30-second query timeout
- Prepared statement support

### 2. `list-tables`

List all tables in the database or specific schema.

**Input:**
```json
{
  "connectionId": "mydb",
  "schema": "public"
}
```

### 3. `list-schemas`

List all schemas/catalogs in the database.

**Input:**
```json
{
  "connectionId": "mydb"
}
```

### 4. `describe-table`

Get detailed table structure including columns, types, primary keys, and indexes.

**Input:**
```json
{
  "connectionId": "mydb",
  "table": "users",
  "schema": "public"
}
```

### 5. `get-row-count`

Get the total number of rows in a table.

**Input:**
```json
{
  "connectionId": "mydb",
  "table": "users",
  "schema": "public"
}
```

### 6. `preview-table`

Get the first N rows from a table (default 10, max 100).

**Input:**
```json
{
  "connectionId": "mydb",
  "table": "users",
  "limit": 20
}
```

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
    "name": "list-tables",
    "arguments": {
      "connectionId": "mydb"
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
2. Register the tool in `Main.java`
3. Rebuild: `mvn clean package`

### Adding New Transport

1. Create a module implementing `McpTransport`
2. Register in `Main.java` as alternative to stdio
3. Common transports: SSE (Server-Sent Events), WebSocket, HTTP

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

