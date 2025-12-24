# JDBC MCP Client

A JavaFX-based GUI client for connecting to and interacting with Model Context Protocol (MCP) servers.

## Quick Start

```bash
# Run the client
./run-client.sh
```

Or using Maven:
```bash
mvn -pl jmcp-client javafx:run
```

## Features

- 🔌 **Easy Server Connection** - Configure and connect to any MCP server with a simple command
- 🛠️ **Tool Discovery** - Automatically list and view all available tools from the server
- 📝 **Dynamic Forms** - Generate input forms automatically based on tool schemas
- ▶️ **Tool Execution** - Execute tools with arguments and view results
- 📄 **Pretty JSON** - View prettified JSON responses for easy reading
- 🎨 **Clean UI** - Simple, intuitive three-panel interface

## Usage

1. **Enter server command** (default: `./run.sh`)
2. **Click Connect** to start the server and initialize
3. **Select a tool** from the list
4. **Fill in arguments** in the generated form
5. **Click Execute** to run the tool
6. **View results** in the right panel

## Architecture

- **McpClientApp** - JavaFX application entry point
- **McpClientController** - UI controller and event handler
- **McpClient** - High-level MCP protocol handler
- **StdioClientTransport** - Process management and stdio communication

## Requirements

- Java 25+
- JavaFX 23.0.1
- Maven 3.9+

## Module

This is a JPMS (Java Platform Module System) module that depends on:
- `org.peacetalk.jmcp.c[run-client.sh](../run-client.sh)ore` - MCP protocol models
- `javafx.controls` - JavaFX UI controls
- `javafx.fxml` - FXML support
- `tools.jackson.databind` - JSON serialization

## Documentation

See [MCP_CLIENT_GUI.md](../claude/MCP_CLIENT_GUI.md) for detailed documentation.

## License

Same as parent project.

