# JDBC MCP Server - Quick Reference

## Commands

### Build
```bash
mvn clean package
```

### Run
```bash
./run.sh
# OR
mvn -q -pl jmcp-server exec:java
```

### Test
```bash
./test-mcp.sh
```

## Configuration

### File Location
- `~/.jmcp/config.json`
- OR `jmcp_CONFIG` environment variable

### Example
```json
{
  "connections": [
    {
      "id": "mydb",
      "databaseType": "postgresql",
      "jdbcUrl": "jdbc:postgresql://localhost:5432/testdb",
      "username": "user",
      "password": "pass"
    }
  ]
}
```

### Database Types
- `postgresql`, `mysql`, `mariadb`, `oracle`, `sqlserver`, `h2`, `derby`, `sqlite`

## Tools Quick Reference

| Tool | Purpose | Key Parameters |
|------|---------|----------------|
| `query` | Execute SELECT | `sql`, `parameters` |
| `list-tables` | List tables | `schema` (optional) |
| `list-schemas` | List schemas | - |
| `describe-table` | Table structure | `table`, `schema` (optional) |
| `get-row-count` | Count rows | `table`, `schema` (optional) |
| `preview-table` | Sample rows | `table`, `limit` (optional) |

## JSON-RPC Examples

### Initialize
```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
```

### List Tools
```json
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
```

### Execute Tool
```json
{
  "jsonrpc":"2.0",
  "id":3,
  "method":"tools/call",
  "params":{
    "name":"query",
    "arguments":{
      "connectionId":"mydb",
      "sql":"SELECT * FROM users LIMIT 10"
    }
  }
}
```

## File Structure

```
jmcp/
├── README.md                    # Main documentation
├── USAGE.md                     # Detailed usage guide
├── ARCHITECTURE.md              # Technical details
├── PROJECT_SUMMARY.md           # Complete overview
├── config.example.json          # Config template
├── run.sh                       # Run script
├── test-mcp.sh                  # Test script
├── pom.xml                      # Parent POM
│
├── jmcp-core/                 # Protocol core
├── jmcp-transport-stdio/      # stdio transport
├── jmcp-jdbc/                 # JDBC tools
├── jmcp-driver-manager/       # Driver loading
└── jmcp-server/               # Main app
```

## Troubleshooting

### Build fails
- Check Java 25+ installed: `java -version`
- Check Maven 3.9+: `mvn -version`

### Driver download fails
- Check internet connection
- Check Maven Central is accessible
- Driver cached in `~/.jmcp/drivers/`

### Connection fails
- Verify JDBC URL format
- Check database is running
- Test credentials with standard JDBC client
- Check network/firewall

### Claude Desktop integration
- Use absolute path in config
- Check stderr output: `tail -f ~/.claude/logs/mcp-server-jmcp.log`
- Restart Claude Desktop after config changes

## Security Checklist

✅ Use read-only database accounts  
✅ Limit connection pool sizes  
✅ Set query timeouts  
✅ Only expose necessary schemas  
✅ Review query logs regularly  
✅ Keep drivers up to date  

## Performance Tips

- Driver download happens once (then cached)
- Connection pools warm up on first query
- Use `preview-table` instead of full `query` for exploration
- Use `get-row-count` before large queries
- Schema metadata is cached by JDBC driver

## Common Tasks

### Add a new database
1. Edit `~/.jmcp/config.json`
2. Add new connection object
3. Restart server

### Update a driver
1. Delete from `~/.jmcp/drivers/`
2. Update version in `JdbcDriverManager.KNOWN_DRIVERS`
3. Rebuild and restart

### Add a custom tool
1. Create class in `jmcp-jdbc/src/main/java/.../tools/`
2. Implement `JdbcTool` interface
3. Register in `Main.java`
4. Rebuild

## Claude Desktop Config Location

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **Linux**: `~/.config/Claude/claude_desktop_config.json`

## Environment Variables

```bash
# Use config from environment
export jmcp_CONFIG='{"connections":[...]}'

# Set driver cache location (default: ~/.jmcp/drivers)
# Note: Not currently supported, but easy to add
```

## Useful Maven Commands

```bash
# Build
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Build single module
mvn -pl jmcp-server clean package

# Run
mvn -pl jmcp-server exec:java

# Clean all
mvn clean

# Install to local repo
mvn clean install
```

## Getting Help

1. Check [USAGE.md](../USAGE.md) for detailed documentation
2. Review [ARCHITECTURE.md](ARCHITECTURE.md) for technical details  
3. Look at [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) for overview
4. Check stderr output for error messages
5. Enable debug logging (if implemented)

