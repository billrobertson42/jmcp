# JDBC MCP Server

A production-ready Model Context Protocol (MCP) server that provides AI assistants like Claude with secure, read-only access to databases via JDBC.

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![MCP](https://img.shields.io/badge/MCP-2024--11--05-green.svg)](https://modelcontextprotocol.io)

## ✨ Features

- **🔌 Protocol Abstraction**: Modular transport layer supporting stdio (with SSE/HTTP planned)
- **📦 Dynamic Driver Loading**: JDBC drivers downloaded from Maven Central on-demand
- **🔒 Isolated ClassLoaders**: Each database connection runs in its own classloader
- **🛡️ Read-Only Tools**: Comprehensive set of safe, read-only database operations
- **🗄️ Multiple Connections**: Support for multiple database connections simultaneously
- **⚡ No Bundled Drivers**: Keeps distribution size small, drivers loaded as needed
- **🏗️ Modular Architecture**: Clean separation of concerns across 5 Maven modules

## 🗃️ Supported Databases

| Database | Version | Driver |
|----------|---------|--------|
| PostgreSQL | Latest | `org.postgresql:postgresql:42.7.1` |
| MySQL | 8.x | `com.mysql:mysql-connector-j:8.3.0` |
| MariaDB | 10.x+ | `org.mariadb.jdbc:mariadb-java-client:3.3.2` |
| Oracle | 11g+ | `com.oracle.database.jdbc:ojdbc11:23.3.0.23.09` |
| SQL Server | 2012+ | `com.microsoft.sqlserver:mssql-jdbc:12.6.0.jre11` |
| H2 | 2.x | `com.h2database:h2:2.2.224` |
| Derby | 10.x | `org.apache.derby:derby:10.17.1.0` |
| SQLite | 3.x | `org.xerial:sqlite-jdbc:3.45.0.0` |

## 🚀 Quick Start

### Prerequisites
- Java 25+ 
- Maven 3.9+
- A database to connect to

### 1. Build

```bash
git clone <your-repo>
cd jmcp
mvn clean package
```

### 2. Configure

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

### 3. Run

```bash
./run.sh
```

The server will:
- Download required JDBC drivers from Maven Central (first run only)
- Cache drivers in `~/.jmcp/drivers/`
- Start listening on stdin for JSON-RPC requests

### 4. Integrate with Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "jmcp": {
      "command": "/absolute/path/to/jmcp/run.sh"
    }
  }
}
```

Restart Claude Desktop and start asking questions about your database!

## 📚 Documentation

- **[USAGE.md](../USAGE.md)** - Detailed usage guide and tool reference
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Technical architecture and design decisions
- **[PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)** - Complete project overview
- **[config.example.json](../config.example.json)** - Configuration examples

## 🛠️ Available Tools

### `query` 
Execute a read-only SELECT query with optional parameters.
- Max 1000 rows
- 30 second timeout
- Prepared statement support

### `list-tables`
List all tables in the database or specific schema.

### `list-schemas`
List all schemas/catalogs in the database.

### `describe-table`
Get detailed table structure including:
- Column names, types, and constraints
- Primary keys
- Indexes

### `get-row-count`
Get the total number of rows in a table.

### `preview-table`
Get the first N rows from a table (default 10, max 100).

## 🏗️ Module Structure

- **`jmcp-core`** - Core MCP protocol interfaces and JSON-RPC handling
- **`jmcp-transport-stdio`** - stdio transport implementation  
- **`jmcp-jdbc`** - JDBC tool implementations and connection management
- **`jmcp-driver-manager`** - Dynamic driver loading from Maven Central
- **`jmcp-server`** - Main application that wires everything together

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design documentation.

## 🔐 Security

- **Read-Only**: Connections set to read-only mode, only SELECT queries allowed
- **SQL Injection Prevention**: Prepared statements used for all queries
- **Query Limits**: Max rows (1000) and timeout (30s) enforced
- **Isolated ClassLoaders**: Driver isolation prevents classpath pollution

## 🧪 Example Usage

### List all tables
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "list-tables",
    "arguments": {
      "connectionId": "mydb"
    }
  }
}
```

### Execute a query
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "query",
    "arguments": {
      "connectionId": "mydb",
      "sql": "SELECT * FROM users WHERE age > ?",
      "parameters": ["18"]
    }
  }
}
```

### Describe a table
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "describe-table",
    "arguments": {
      "connectionId": "mydb",
      "table": "users",
      "schema": "public"
    }
  }
}
```

## 🤝 Contributing

Contributions welcome! Areas for enhancement:
- Additional database tools
- SSE/HTTP transport implementations  
- MCP Resources support (expose schemas as resources)
- MCP Prompts support (query templates)
- Query optimization suggestions
- Integration tests

## 📄 License

TBD

## 🙏 Acknowledgments

- Built for the [Model Context Protocol](https://modelcontextprotocol.io)
- Uses [HikariCP](https://github.com/brettwooldridge/HikariCP) for connection pooling
- Uses [Jackson](https://github.com/FasterXML/jackson) for JSON processing
