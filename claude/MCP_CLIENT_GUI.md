# MCP Client GUI

## Overview

The `jmcp-client` module provides a JavaFX-based graphical user interface for connecting to and interacting with Model Context Protocol (MCP) servers. It allows users to:

- Configure and launch MCP servers
- View available tools
- Execute tools with custom arguments
- View prettified JSON results

## Features

### 1. Server Connection Management
- Configure server command (e.g., `./run.sh`, `python server.py`, etc.)
- Connect/disconnect from servers
- View connection status
- Automatic process management

### 2. Tool Discovery
- Automatic tool listing upon connection
- View tool descriptions
- View tool input schemas (JSON Schema)

### 3. Tool Execution
- Dynamic form generation based on tool schema
- Support for required and optional arguments
- Automatic type detection (string, number, boolean, JSON arrays)
- Execute tools and view results
- Pretty-printed JSON output

### 4. User Interface
- Three-panel layout:
  - **Left**: Available tools list
  - **Middle**: Tool details and argument input
  - **Right**: Results display
- Status bar with connection information
- Easy-to-use form-based argument entry

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        McpClientApp                              │
│                    (JavaFX Application)                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    McpClientController                           │
│                    (FXML Controller)                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        McpClient                                 │
│                   (Protocol Handler)                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  StdioClientTransport                            │
│              (Process Management & I/O)                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
                    ┌────────────────┐
                    │  MCP Server    │
                    │  (External)    │
                    └────────────────┘
```

## Classes

### McpClientApp
Main JavaFX application entry point. Loads the FXML layout and initializes the UI.

**Key Methods:**
- `start(Stage)` - Initializes and shows the primary stage
- `main(String[])` - Application entry point

### McpClientController
JavaFX controller that handles UI events and manages the MCP client connection.

**Key Methods:**
- `initialize()` - Sets up UI components and event handlers
- `onConnect()` - Connects to MCP server and loads tools
- `onDisconnect()` - Disconnects from server and cleans up
- `onToolSelected(Tool)` - Updates UI when a tool is selected
- `onExecute()` - Executes the selected tool with provided arguments
- `buildArgumentFields(Tool)` - Dynamically creates input fields based on tool schema
- `parseValue(String)` - Intelligently parses input values (numbers, booleans, JSON, strings)

### McpClient
High-level MCP client that manages protocol operations.

**Key Methods:**
- `connect()` - Starts server process and performs MCP initialization
- `listTools()` - Retrieves available tools from the server
- `callTool(String, Map)` - Executes a tool with given arguments
- `getServerInfo()` - Returns server information from initialization
- `isConnected()` - Checks if server process is still running

### StdioClientTransport
Low-level transport layer that manages the server process and stdio communication.

**Key Methods:**
- `connect()` - Launches the server process
- `sendRequest(String, Object)` - Sends JSON-RPC request and waits for response
- `isConnected()` - Checks process liveness
- `close()` - Terminates server process and closes streams

## Usage

### Running the Client

```bash
# From project root
./run-client.sh

# Or using Maven directly
mvn -pl jmcp-client javafx:run
```

### Connecting to a Server

1. Enter the server command in the text field (default: `./run.sh`)
   - Can be any command: `./run.sh`, `python server.py`, `node server.js`, etc.
   - Arguments can be included: `./run.sh --config config.json`

2. Click "Connect"
   - The client will launch the server process
   - Perform MCP initialization handshake
   - Load available tools

3. View connection status in the status bar

### Using Tools

1. Select a tool from the list on the left
2. View tool description and input schema in the middle panel
3. Fill in required arguments in the dynamically generated form
4. Click "Execute Tool"
5. View pretty-printed JSON results on the right

### Argument Entry

The client automatically detects argument types:

- **Numbers**: `42`, `3.14` → parsed as Integer/Double
- **Booleans**: `true`, `false` → parsed as Boolean
- **JSON Arrays**: `["value1", "value2"]` → parsed as JSON array
- **Strings**: Everything else → treated as string

### Example Workflow

```
1. Connect to JDBC MCP Server
   Command: ./run.sh

2. Select "query" tool

3. Enter arguments:
   sql: SELECT * FROM users WHERE id = ?
   parameters: ["123"]

4. Click "Execute Tool"

5. View results:
   {
     "content": [
       {
         "type": "text",
         "text": "{\"rows\": [...], \"columns\": [...]}"
       }
     ]
   }
```

## Configuration

The client uses the MCP models from `jmcp-core` for JSON serialization, ensuring compatibility with the server.

### Default Server Command

The default server command is `./run.sh`, which starts the JDBC MCP Server. You can change this to connect to any MCP-compatible server.

### FXML Layout

The UI layout is defined in `McpClient.fxml` using JavaFX FXML. The layout uses a three-panel split pane for optimal workspace organization.

## Dependencies

### Runtime Dependencies
- **JavaFX 23.0.1** - UI framework
  - javafx-controls
  - javafx-fxml
- **jmcp-core** - MCP protocol models
- **Jackson** - JSON serialization (via core)

### Build Dependencies
- **Maven** - Build tool
- **javafx-maven-plugin** - JavaFX build support

## Module System (JPMS)

The client is a Java Platform Module System (JPMS) module:

```java
module org.peacetalk.jmcp.client {
    requires org.peacetalk.jmcp.core;
    requires javafx.controls;
    requires javafx.fxml;
    requires tools.jackson.databind;
    
    exports org.peacetalk.jmcp.client;
    opens org.peacetalk.jmcp.client to javafx.fxml;
}
```

The module:
- Requires core MCP models and JavaFX
- Exports client package for public API
- Opens package to javafx.fxml for reflection-based FXML loading

## Error Handling

The client handles various error scenarios:

- **Connection Failures**: Shows error dialog if server fails to start
- **Communication Errors**: Displays error if server closes unexpectedly
- **Tool Execution Errors**: Shows error message in results area
- **Argument Parsing**: Handles malformed JSON gracefully

All network operations run in background threads to keep the UI responsive.

## Future Enhancements

Potential improvements:

1. **Persistent Configuration**: Save/load server commands and connection history
2. **Multiple Servers**: Support connecting to multiple servers simultaneously
3. **Result History**: Keep history of tool executions
4. **Export Results**: Save results to file
5. **Syntax Highlighting**: Add JSON syntax highlighting in result area
6. **Schema Validation**: Validate arguments before sending
7. **Auto-completion**: Suggest values based on schema
8. **Dark Theme**: Add dark mode support
9. **Tool Favorites**: Bookmark frequently used tools
10. **Batch Execution**: Execute multiple tools in sequence

## Screenshots

### Main Window
```
┌────────────────────────────────────────────────────────────────┐
│ Server Command: [./run.sh        ] [Connect] [Disconnect]     │
│ Status: Connected: jmcp-server v1.0-SNAPSHOT                │
├────────────┬──────────────────────┬──────────────────────────┤
│ Tools      │ Tool Details         │ Result                   │
├────────────┤                      │                          │
│ query      │ Description:         │                          │
│ list-tables│ Execute SQL query... │                          │
│ describe   │                      │                          │
│ preview    │ Input Schema:        │ {                        │
│ ...        │ {                    │   "content": [           │
│            │   "type": "object",  │     ...                  │
│            │   "properties": ...  │   ]                      │
│            │ }                    │ }                        │
│            │                      │                          │
│            │ Arguments:           │                          │
│            │ sql *: [________]    │                          │
│            │ params: [________]   │                          │
│            │                      │                          │
│            │ [Execute Tool]       │                          │
└────────────┴──────────────────────┴──────────────────────────┘
│ JDBC MCP Client v1.0                                          │
└────────────────────────────────────────────────────────────────┘
```

## Troubleshooting

### Server Won't Start
- Check that the server command is correct
- Ensure the server script is executable (`chmod +x run.sh`)
- Check server logs (stderr is redirected to console)

### Connection Lost
- Server may have crashed - check server logs
- Try reconnecting
- Restart the client if needed

### Tool Execution Fails
- Check that all required arguments are provided
- Verify argument values are correctly formatted
- Check server logs for detailed error messages

## Development

### Building
```bash
mvn clean package -pl jmcp-client -am
```

### Running from Maven
```bash
mvn -pl jmcp-client javafx:run
```

### Running from IDE
Run `McpClientApp.main()` with JavaFX VM options:
```
--module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml
```

---

*Last updated: November 28, 2025*

