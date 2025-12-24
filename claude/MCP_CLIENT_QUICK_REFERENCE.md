# MCP Client Quick Reference

## Starting the Client

```bash
./run-client.sh
```

## Basic Workflow

### 1. Connect to Server
```
┌─────────────────────────────────────────────┐
│ Server Command: [./run.sh        ]         │
│                 [Connect] [Disconnect]      │
└─────────────────────────────────────────────┘
```

1. Enter server command (default: `./run.sh`)
2. Click **Connect**
3. Wait for "Connected: ..." status

### 2. Select a Tool
```
┌──────────────┐
│ Tools        │
├──────────────┤
│ query        │ ← Click to select
│ list-tables  │
│ describe     │
│ preview      │
└──────────────┘
```

### 3. Fill Arguments
```
┌─────────────────────────┐
│ Arguments               │
├─────────────────────────┤
│ sql *: [SELECT * FROM…] │
│ parameters: [["123"]]   │
└─────────────────────────┘
```
- Required fields marked with *
- Type values directly

### 4. Execute Tool
```
[Execute Tool]
```
Click button and wait for results

### 5. View Results
```
┌─────────────────────────┐
│ Result                  │
├─────────────────────────┤
│ {                       │
│   "content": [{         │
│     "type": "text",     │
│     "text": "..."       │
│   }]                    │
│ }                       │
└─────────────────────────┘
```
Pretty-printed JSON output

## Argument Types

| Type | Example | Description |
|------|---------|-------------|
| String | `hello` | Plain text |
| Number | `42` or `3.14` | Integer or decimal |
| Boolean | `true` or `false` | Boolean value |
| JSON Array | `["a", "b"]` | JSON array string |

## Common Server Commands

| Server Type | Command |
|-------------|---------|
| Local JDBC Server | `./run.sh` |
| Python MCP | `python server.py` |
| Node.js MCP | `node server.js` |
| Custom script | `/path/to/script.sh --args` |

## Example: Query Tool

### SQL Query
```
Tool: query
Arguments:
  sql: SELECT * FROM users WHERE id = ?
  parameters: ["123"]
```

### Result
```json
{
  "content": [{
    "type": "text",
    "text": "{\"rows\": [...], \"columns\": [...]}"
  }]
}
```

## Example: List Tables
```
Tool: list-tables
Arguments:
  schemaName: public

Result:
{
  "content": [{
    "type": "text",
    "text": "{\"tables\": [\"users\", \"orders\", ...]}"
  }]
}
```

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Tab | Navigate between fields |
| Enter | Execute (when Execute button focused) |
| Ctrl+A | Select all in text areas |

## Troubleshooting

### Server Won't Start
- ✓ Check command is correct
- ✓ Verify script is executable (`chmod +x`)
- ✓ Check server logs in console

### Connection Lost
- ✓ Server may have crashed
- ✓ Click Disconnect then reconnect
- ✓ Check server stderr output

### Tool Execution Fails
- ✓ Verify all required arguments (*) are filled
- ✓ Check argument format (especially JSON arrays)
- ✓ View error message in result area

### JSON Array Format
```
✓ Correct:  ["value1", "value2"]
✗ Wrong:    [value1, value2]
✗ Wrong:    value1, value2
```

## Tips

💡 **Save Time**: Default command is remembered between uses

💡 **Copy Results**: Click in result area and Ctrl+A, Ctrl+C to copy

💡 **Test Queries**: Start with simple queries before complex ones

💡 **Schema First**: Use list-schemas and list-tables to explore database

💡 **Describe Tables**: Use describe-table before writing queries

## Layout

```
┌────────────────────────────────────────────────────────────┐
│ Connection Bar                                             │
├──────────┬──────────────────┬──────────────────────────────┤
│          │                  │                              │
│  Tools   │  Tool Details    │       Results                │
│  List    │  & Arguments     │                              │
│          │                  │                              │
│  (Left)  │     (Middle)     │       (Right)                │
│          │                  │                              │
└──────────┴──────────────────┴──────────────────────────────┘
│ Status Bar                                                 │
└────────────────────────────────────────────────────────────┘
```

## Advanced

### Multiple Connections
To connect to different servers, disconnect and enter new command.

### Custom Server Arguments
```
./run.sh --config /path/to/config.json
python server.py --port 8080
```

### Background Operation
Server runs as child process. Closing client stops server.

---

**Need Help?** Check the full documentation in [MCP_CLIENT_GUI.md](MCP_CLIENT_GUI.md)

