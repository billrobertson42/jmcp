# MCP Client GUI

## Overview

The `jmcp-client` module provides a JavaFX-based graphical user interface for connecting to and interacting with Model Context Protocol (MCP) servers. It allows users to:

- Configure and launch MCP servers
- View available tools and resources
- Execute tools with custom arguments
- Browse database resources with navigable URI-based navigation
- View prettified JSON results
- Log protocol communication

## Features

### 1. Server Connection Management
- Configure server command (e.g., `./run.sh`, `python server.py`, etc.)
- Connect/disconnect from servers
- View connection status
- Automatic process management
- Persistent preferences for server command

### 2. Tool Discovery & Execution
- Automatic tool listing upon connection
- View tool descriptions and input schemas
- Dynamic form generation based on tool schema
- Support for required and optional arguments
- Automatic type detection (string, number, boolean, JSON arrays)
- Pretty-printed JSON output

### 3. Resource Navigation
- Browse database resources via HATEOAS-like URI navigation
- Navigate connection → schema → table/view/procedure hierarchy
- Back/forward navigation history
- Clickable URI links in resource content
- Resource proxy support for complete database exploration

### 4. Communication Logging
- Protocol message logging for debugging
- View JSON-RPC requests and responses

### 5. Accessibility
- Accessibility helper for screen readers
- Keyboard navigation support

### 6. User Interface
- Three-panel layout:
  - **Left**: Available tools list + resource browser
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
└──────────┬─────────────────────────────┬────────────────────────┘
           │                             │
           ▼                             ▼
┌────────────────────────┐    ┌─────────────────────────┐
│     McpClient          │    │   UI Components (ui/)   │
│  (Protocol Handler)    │    │  ToolArgumentFormBuilder │
└──────────┬─────────────┘    │  ToolListCell            │
           │                  │  ResourceListCell        │
           ▼                  │  NavigableResourceView   │
┌────────────────────────┐    │  NavigableUriDetector    │
│ StdioClientTransport   │    │  ResourceNavigationHist. │
│ (Process Mgmt & I/O)   │    │  ValueParser             │
└──────────┬─────────────┘    └─────────────────────────┘
           │
           ▼                  ┌─────────────────────────┐
  ┌────────────────┐          │  Services (service/)    │
  │  MCP Server    │          │  McpService             │
  │  (External)    │          │  CommunicationLogger    │
  └────────────────┘          └─────────────────────────┘
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
- Resource navigation methods for browsing database structure

### McpClient
High-level MCP client that manages protocol operations.

**Key Methods:**
- `connect()` - Starts server process and performs MCP initialization
- `listTools()` - Retrieves available tools from the server
- `callTool(String, Map)` - Executes a tool with given arguments
- `getServerInfo()` - Returns server information from initialization
- `isConnected()` - Checks if server process is still running
- Resource protocol operations (list, read)

### StdioClientTransport
Low-level transport layer that manages the server process and stdio communication.

**Key Methods:**
- `connect()` - Launches the server process
- `sendRequest(String, Object)` - Sends JSON-RPC request and waits for response
- `isConnected()` - Checks process liveness
- `close()` - Terminates server process and closes streams

### ClientPreferences
Manages persistent user preferences using `java.util.prefs`.

### AccessibilityHelper
Provides accessibility support for screen reader compatibility.

### CommunicationListener / DisplayContent / DisplayResult
Interfaces for communication event handling and display content rendering.

### service/McpService
Service layer for MCP protocol operations.

### service/CommunicationLogger
Logs protocol messages for debugging and auditing.

### ui/ToolArgumentFormBuilder
Dynamically creates input fields based on tool JSON Schema definitions.

### ui/ToolListCell / ui/ResourceListCell
Custom cell renderers for tool and resource list views.

### ui/NavigableResourceView
Provides a navigable resource browser with URI-based navigation.

### ui/NavigableUriDetector
Detects clickable URIs in resource content for navigation.

### ui/ResourceNavigationHistory
Manages back/forward navigation history for resource browsing.

### ui/ValueParser
Intelligently parses input values (numbers, booleans, JSON, strings).

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
   - Load available tools and resources

3. View connection status in the status bar

### Using Tools

1. Select a tool from the list on the left
2. View tool description and input schema in the middle panel
3. Fill in required arguments in the dynamically generated form
4. Click "Execute Tool"
5. View pretty-printed JSON results on the right

### Browsing Resources

1. Navigate the resource tree starting from `db://context`
2. Click on URIs in resource content to navigate deeper
3. Use back/forward buttons for navigation history
4. Browse connections → schemas → tables → columns/relationships

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
   sql: SELECT * FROM users LIMIT 10

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

The default server command is `./run.sh`, which starts the JDBC MCP Server. You can change this to connect to any MCP-compatible server. The last-used server command is persisted via `ClientPreferences`.

### FXML Layout

The UI layout is defined in `McpClient.fxml` using JavaFX FXML. The layout uses a three-panel split pane for optimal workspace organization.

## Dependencies

### Runtime Dependencies
- **JavaFX 25.0.1** - UI framework
  - javafx-controls
  - javafx-fxml
- **jmcp-core** - MCP protocol models (tools + resources)
- **Jackson 3.x** - JSON serialization (via core)
- **Log4j2** - Logging
- **java.prefs** - Persistent preferences

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
    requires java.prefs;
    requires org.apache.logging.log4j;

    exports org.peacetalk.jmcp.client;
    opens org.peacetalk.jmcp.client to javafx.fxml;

    exports org.peacetalk.jmcp.client.service to org.peacetalk.jmcp.client.test;
    exports org.peacetalk.jmcp.client.ui to org.peacetalk.jmcp.client.test;
}
```

The module:
- Requires core MCP models, JavaFX, Jackson, preferences, and logging
- Exports client package for public API
- Opens package to javafx.fxml for reflection-based FXML loading
- Exports service and UI subpackages to test module (qualified)

## Error Handling

The client handles various error scenarios:

- **Connection Failures**: Shows error dialog if server fails to start
- **Communication Errors**: Displays error if server closes unexpectedly
- **Tool Execution Errors**: Shows error message in results area
- **Argument Parsing**: Handles malformed JSON gracefully
- **Resource Navigation**: Handles missing or invalid resource URIs

All network operations run in background threads to keep the UI responsive.

## Screenshots

### Main Window
```
┌────────────────────────────────────────────────────────────────┐
│ Server Command: [./run.sh        ] [Connect] [Disconnect]     │
│ Status: Connected: jmcp-server v1.0.0-SNAPSHOT                │
├────────────┬──────────────────────┬──────────────────────────┤
│ Tools      │ Tool Details         │ Result                   │
├────────────┤                      │                          │
│ query      │ Description:         │                          │
│ explain-q. │ Execute SQL query... │                          │
│ get-row-c. │                      │                          │
│ sample-d.  │ Input Schema:        │ {                        │
│ analyze-c. │ {                    │   "content": [           │
│ resource-p.│   "type": "object",  │     ...                  │
│            │   "properties": ...  │   ]                      │
│ Resources: │ }                    │ }                        │
│ db://ctx   │                      │                          │
│ db://conns │ Arguments:           │                          │
│            │ sql *: [________]    │                          │
│            │ database_id: [____]  │                          │
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

*Updated: April 14, 2026*
