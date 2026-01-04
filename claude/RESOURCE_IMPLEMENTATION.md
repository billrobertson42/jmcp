# MCP Resources Implementation

## Summary

This document summarizes the implementation of MCP resources support for the JDBC MCP server and client, providing a HATEOAS-style navigational approach to exploring database schemas and structure.

## Implementation Date

January 1, 2026

## Components Created/Modified

### Core Module (jmcp-core)

1. **ResourceProvider Interface** (`org.peacetalk.jmcp.core.ResourceProvider`)
   - Defines the contract for components that provide MCP resources
   - Methods: `initialize()`, `listResources(cursor)`, `getResource(uri)`, `supportsScheme(scheme)`, `shutdown()`, `getName()`
   - Supports HATEOAS-style navigation with hierarchical URIs

2. **Resource Interface** (`org.peacetalk.jmcp.core.Resource`)
   - Represents a single MCP resource
   - Methods: `getUri()`, `getName()`, `getDescription()`, `getMimeType()`, `read()`

3. **ResourceDescriptor Record** (`org.peacetalk.jmcp.core.model.ResourceDescriptor`)
   - MCP protocol record for resource metadata in `resources/list` responses
   - Fields: `uri`, `name`, `description`, `mimeType`

4. **ResourcesHandler** (`org.peacetalk.jmcp.core.protocol.ResourcesHandler`)
   - MCP protocol handler for `resources/list` and `resources/read` methods
   - Aggregates resources from multiple ResourceProviders
   - Routes requests based on URI scheme

### JDBC Module (jmcp-jdbc)

1. **JdbcResourceProvider** (`org.peacetalk.jmcp.jdbc.resources.JdbcResourceProvider`)
   - Implements `ResourceProvider` for JDBC database resources
   - URI scheme: `jdbc://`
   - Handles URI routing to appropriate resource types

2. **Resource Classes** (all in `org.peacetalk.jmcp.jdbc.resources`):
   - **ConnectionsListResource** - Lists all database connections (`jdbc://connections`)
   - **ConnectionResource** - Single connection details (`jdbc://connection/{id}`)
   - **SchemasListResource** - Lists schemas in a connection (`jdbc://connection/{id}/schemas`)
   - **SchemaResource** - Single schema details (`jdbc://connection/{id}/schema/{schema}`)
   - **TablesListResource** - Lists tables in a schema (`jdbc://connection/{id}/schema/{schema}/tables`)
   - **TableResource** - Table structure with columns, PKs, indexes, FKs (`jdbc://connection/{id}/schema/{schema}/table/{table}`)
   - **ViewsListResource** - Lists views in a schema (`jdbc://connection/{id}/schema/{schema}/views`)
   - **ViewResource** - View structure with columns (`jdbc://connection/{id}/schema/{schema}/view/{view}`)

### Server Module (jmcp-server)

- **Main.java** updated to register `ResourcesHandler` with `JdbcResourceProvider`

### Client Module (jmcp-client)

1. **McpClient** - Added methods:
   - `listResources()` - List all resources
   - `listResources(String cursor)` - List resources with pagination
   - `readResource(String uri)` - Read a resource by URI

2. **McpService** - Added methods:
   - `listResources()` / `listResources(String cursor)` - Service layer for listing resources
   - `sortResources(List<ResourceDescriptor>)` - Sort resources alphabetically
   - `readResource(String uri)` - Service layer for reading resources

3. **McpClientController** - Added:
   - Resources tab in the UI alongside Tools tab
   - Resource ComboBox with ResourceListCell for display
   - Resource details panel showing URI, name, description, MIME type
   - "Read Resource" button to fetch and display resource content
   - Resource selection handler and state management

4. **ResourceListCell** (new) - Custom ListCell for displaying resources in ComboBox

5. **McpClient.fxml** - UI changes:
   - Added TabPane with "Tools" and "Resources" tabs
   - Resources tab contains:
     - Resource selection dropdown
     - "Read Resource" button
     - Resource details panel
     - URI display field
   - Output panel changes:
     - Single "Result" tab with StackPane for view switching
     - Tool result view (TextArea) - visible when executing tools
     - Resource result view (VBox) - visible when reading resources
       - Back navigation button with history depth display
       - Breadcrumb showing current URI
       - NavigableResourceView for clickable URI links
     - Both views preserve state when switching between them (managed/visible properties)

6. **ResourceNavigationHistory** (new) - Stack-based navigation history:
   - `navigateTo(uri, content)` - Navigate to new resource, push current to history
   - `goBack()` - Pop previous entry from stack (discards current)
   - `canGoBack()` - Check if back navigation is available
   - `historyDepth()` - Get number of entries in back history
   - `clear()` - Clear all history

7. **NavigableUriDetector** (new) - URI detection utility:
   - Identifies known URI field names (uri, resourceUri, schemasUri, tablesUri, viewsUri, parent, referencedTableUri, etc.)
   - Pattern-based detection of navigable URIs in JSON content
   - Returns positions for creating clickable hyperlinks

8. **NavigableResourceView** (new) - Custom ScrollPane component:
   - Displays JSON content with clickable URI hyperlinks
   - Uses TextFlow with mixed Text and Hyperlink nodes
   - Monospace font styling for JSON readability
   - Callback mechanism for handling link clicks

### Resource Navigation Features

- **JSON Decoding**: The "Decode JSON in text fields" checkbox now applies to resource results
- **Clickable URIs**: Fields like `uri`, `resourceUri`, `schemasUri`, etc. are rendered as clickable links
- **Back Navigation**: Stack-based history allows navigating back through previously viewed resources
- **Tab-Based View Switching**: Single "Result" tab switches between tool and resource views based on selected left tab
  - Selecting "Tools" tab shows the tool result view
  - Selecting "Resources" tab shows the resource result view with navigation
  - Each view preserves its state when switching (no data loss)
  - View switching is independent of executing tools or reading resources

## URI Hierarchy

```
jdbc://connections                                    → List of all connections
jdbc://connection/{id}                                → Connection details
jdbc://connection/{id}/schemas                        → List of schemas
jdbc://connection/{id}/schema/{schema}                → Schema details
jdbc://connection/{id}/schema/{schema}/tables         → List of tables
jdbc://connection/{id}/schema/{schema}/table/{table}  → Table structure
jdbc://connection/{id}/schema/{schema}/views          → List of views
jdbc://connection/{id}/schema/{schema}/view/{view}    → View structure
```

## HATEOAS Navigation

Each resource includes navigation links to related resources:
- **Parent link**: Navigate up the hierarchy
- **Child links**: Navigate to contained resources (e.g., schemas link from connections, tables link from schema)
- **Reference links**: Navigate to referenced resources (e.g., foreign key targets)

## Tests Created

### Core Tests
- `ResourcesHandlerTest` - Tests for the MCP protocol handler (10 tests)

### JDBC Tests
- `JdbcResourcesTest` - Tests for individual resource classes (17 tests)
- `JdbcResourceProviderTest` - Tests for URI routing and resource lookup (17 tests)

## MCP Protocol Support

The implementation supports:
- `resources/list` - Lists available resources with optional pagination cursor
- `resources/read` - Reads resource content by URI

Server capabilities advertise resources support in the `initialize` response.

## Usage Example

```json
// Request: List resources
{"jsonrpc": "2.0", "id": 1, "method": "resources/list", "params": {}}

// Response
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "resources": [
      {"uri": "jdbc://connections", "name": "Database Connections", "mimeType": "application/json"},
      {"uri": "jdbc://connection/mydb", "name": "Connection: mydb", "mimeType": "application/json"}
    ]
  }
}

// Request: Read a resource
{"jsonrpc": "2.0", "id": 2, "method": "resources/read", "params": {"uri": "jdbc://connection/mydb/schemas"}}

// Response
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "contents": [{
      "uri": "jdbc://connection/mydb/schemas",
      "mimeType": "application/json",
      "text": "{\"schemas\":[{\"name\":\"public\",\"isDefault\":true}],\"count\":1}"
    }]
  }
}
```

## Build Verification

All tests pass:
- jmcp-core: 76 tests
- jmcp-jdbc: 401 tests  
- jmcp-client: 64 tests (includes ResourceNavigationHistoryTest, NavigableUriDetectorTest)
- Total: **541 tests, 0 failures**

