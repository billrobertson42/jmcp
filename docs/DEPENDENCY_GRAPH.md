# JDBC MCP Server - Dependency Graph

## ASCII Art Dependency Graph

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                    Main                                     │
│                        (jmcp-server/Main.java)                            │
│                    SPI Consumer — zero compile-time deps                   │
│                    on jmcp-jdbc or jmcp-transport-stdio                    │
└────────────────┬────────────────────────────┬────────────────┬──────────────┘
                 │                            │                │
                 ▼                            ▼                ▼
        ┌────────────────┐         ┌─────────────────┐   ┌──────────────────┐
        │  McpServer     │         │ ServiceLoader    │   │ Configuration    │
        │  (core)        │         │ Discovery        │   │ (JSON config)    │
        └────┬───────────┘         └────┬────────────┘   └──────────────────┘
             │                          │
             │                          ├─→ TransportProvider (SPI)
             │                          │     └─→ StdioTransportProvider
             │                          │
             │                          └─→ McpProvider (SPI)
             │                                └─→ JdbcMcpProvider
             │
             │ registers handlers via getSupportedMethods()
             ├─────────────────────┬──────────────────────┐
             ▼                     ▼                      ▼
   ┌───────────────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │ InitializationHandler │  │ ToolsHandler     │  │ ResourcesHandler │
   │ (core/protocol)       │  │ (core/protocol)  │  │ (core/protocol)  │
   └───────────────────────┘  └────┬─────────────┘  └────┬─────────────┘
                                   │                      │
                                   │ aggregates           │ aggregates
                                   │ McpProvider.getTools()│ McpProvider.getResourceProvider()
                                   ▼                      ▼
                          ┌──────────────────┐   ┌──────────────────────┐
                          │  Tool (core)     │   │ ResourceProvider     │
                          │  (interface)     │   │ (core interface)     │
                          └──────┬───────────┘   └────┬─────────────────┘
                                 │                     │
                                 │ implemented by      │ implemented by
                                 ▼                     ▼
                        ┌─────────────────┐   ┌──────────────────────┐
                        │ JdbcToolAdapter  │   │ JdbcResourceProvider │
                        │ (jdbc)          │   │ (jdbc/resources)     │
                        └───┬─────────────┘   └──────────────────────┘
                            │
                            │ wraps JdbcTool with ConnectionContext
                            ▼
                     ┌──────────────┐
                     │ JdbcTool     │
                     │ (interface)  │
                     └──────┬───────┘
                            │
                            │ implemented by
                            ├────────────────┬────────────────┐
                            ▼                ▼                ▼
                  ┌─────────────┐ ┌──────────────────┐ ┌────────────────────┐
                  │ QueryTool   │ │ ExplainQueryTool │ │ GetRowCountTool    │
                  │ (jdbc/tools)│ │ (jdbc/tools)     │ │ (jdbc/tools)       │
                  └─────────────┘ └──────────────────┘ └────────────────────┘
                  ┌──────────────────┐ ┌──────────────────┐
                  │ SampleDataTool   │ │ AnalyzeColumnTool│
                  │ (jdbc/tools)     │ │ (jdbc/tools)     │
                  └──────────────────┘ └──────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                         Core Protocol Models                                 │
└─────────────────────────────────────────────────────────────────────────────┘

   ┌──────────────────────────────────────┐
   │         Core Models                  │
   │    (core/model package)              │
   ├──────────────────────────────────────┤
   │  • JsonRpcRequest                    │
   │  • JsonRpcResponse                   │
   │  • JsonRpcError                      │
   │  • InitializeRequest                 │
   │  • InitializeResult                  │
   │  • ServerCapabilities                │
   │  • ClientCapabilities                │
   │  • Implementation                    │
   │  • Tool (model record)              │
   │  • CallToolRequest                   │
   │  • CallToolResult                    │
   │  • ListToolsResult                   │
   │  • Content                           │
   │  • ResourceDescriptor                │
   │  • ListResourcesResult              │
   │  • ReadResourceRequest              │
   │  • ReadResourceResult               │
   │  • ResourceContents                  │
   └──────────────────────────────────────┘
             │
             │ validated by
             ▼
   ┌──────────────────────────────────────┐
   │       McpValidator                   │
   │   (core/validation)                  │
   │   Uses: Hibernate Validator          │
   └──────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                         JDBC Resources System                                │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌──────────────────────┐
                    │ JdbcResourceProvider │
                    │ (jdbc/resources)     │
                    └────┬─────────────────┘
                         │
                         │ creates & manages
                         ├──────────────┬──────────────┬──────────────┐
                         ▼              ▼              ▼              ▼
              ┌──────────────────┐ ┌─────────────┐ ┌────────────┐ ┌──────────────┐
              │ ContextResource  │ │Connections-  │ │Connection- │ │SchemasLlist- │
              │ (db://context)   │ │ListResource │ │Resource    │ │Resource      │
              └──────────────────┘ └─────────────┘ └────────────┘ └──────────────┘
              ┌──────────────────┐ ┌─────────────┐ ┌────────────┐ ┌──────────────┐
              │ SchemaResource   │ │TablesListR. │ │TableRes.   │ │ViewsListR.   │
              └──────────────────┘ └─────────────┘ └────────────┘ └──────────────┘
              ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────┐
              │ ViewResource     │ │ProcedureResource  │ │RelationshipsResource │
              └──────────────────┘ └──────────────────┘ └──────────────────────┘
              ┌──────────────────────────┐ ┌──────────────────────────┐
              │SchemaRelationshipsRes.   │ │TopologicalSort (utility) │
              └──────────────────────────┘ └──────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                     Tool Result Records                                      │
└─────────────────────────────────────────────────────────────────────────────┘

   ┌──────────────────────────────────────┐
   │    Tool Result Records              │
   │    (jdbc/tools/results)             │
   ├──────────────────────────────────────┤
   │  • QueryResult                      │
   │  • CompactQueryResult               │
   │  • ExplainQueryResult               │
   │  • RowCountResult                   │
   │  • TablePreviewResult               │
   │  • CompactTablePreviewResult        │
   │  • ColumnAnalysis                   │
   │  • ColumnMetadata                   │
   │  • ValueFrequency                   │
   │  • TableDescription                 │
   │  • TableInfo                        │
   │  • TablesListResult                 │
   │  • TableStatistics                  │
   │  • SchemaInfo                       │
   │  • SchemasListResult               │
   │  • ViewInfo                         │
   │  • ViewsListResult                  │
   │  • IndexInfo                        │
   │  • ForeignKeyInfo                   │
   │  • CheckConstraintInfo              │
   │  • TriggerInfo                      │
   │  • PartitionInfo                    │
   │  • ProcedureInfo                    │
   │  • ProcedureParameter               │
   │  • ProceduresListResult             │
   │  • ConnectionInfo                   │
   │  • ListConnectionsResult            │
   └──────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                     Tool Schema & Validation                                 │
└─────────────────────────────────────────────────────────────────────────────┘

         ┌──────────────────┐
         │   JdbcTool       │
         │   (interface)    │
         └────┬─────────────┘
              │
              │ defines schema using
              ▼
    ┌──────────────────────────────┐
    │    Schema Records            │
    │    (core/schema)             │
    ├──────────────────────────────┤
    │  • ObjectSchema              │
    │  • StringProperty            │
    │  • IntegerProperty           │
    │  • BooleanProperty           │
    │  • ArrayProperty             │
    └────┬─────────────────────────┘
         │
         │ SQL validated by
         ▼
    ┌──────────────────────────────┐
    │  ReadOnlySqlValidator        │
    │  (jdbc/validation)           │
    │  Uses: JSqlParser            │
    └──────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                          Data Flow Summary                                   │
└─────────────────────────────────────────────────────────────────────────────┘

1. Client sends JSON-RPC request via stdin
                 │
                 ▼
2. StdioTransport receives and passes to McpServer
                 │
                 ▼
3. McpServer parses JsonRpcRequest
                 │
                 ▼
4. McpServer O(1) HashMap lookup for McpProtocolHandler
                 │
                 ├─ initialize → InitializationHandler
                 │                      │
                 │                      └─ returns ServerCapabilities
                 │
                 ├─ tools/list → ToolsHandler
                 │                      │
                 │                      └─ returns ListToolsResult
                 │
                 ├─ tools/call → ToolsHandler
                 │                      │
                 │                      ├─ O(1) lookup by tool name
                 │                      ├─ tool.execute(params)
                 │                      │    (JdbcToolAdapter resolves connection)
                 │                      └─ returns CallToolResult
                 │
                 ├─ resources/list → ResourcesHandler
                 │                      │
                 │                      └─ returns ListResourcesResult
                 │
                 └─ resources/read → ResourcesHandler
                                        │
                                        ├─ routes by URI scheme
                                        ├─ resource.read()
                                        └─ returns ReadResourceResult
                 │
                 ▼
5. McpServer serializes JsonRpcResponse
                 │
                 ▼
6. StdioTransport writes response to stdout


┌─────────────────────────────────────────────────────────────────────────────┐
│                          Module Dependencies                                 │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  jmcp-server     │
│  (Main entry)    │
│  SPI consumer    │
└────┬─────────────┘
     │
     │ compile: jmcp-core only
     │ runtime: discovers via ServiceLoader
     ├─────────────────────────────────────────────┐
     ▼                                              ▼
┌────────────┐                              ┌──────────────────┐
│ jmcp-core  │                              │ jmcp-server/tools│
│            │                              │ (ResourceProxy)  │
└────────────┘                              └──────────────────┘
     ▲                ▲
     │                │
     │ requires       │ requires
     │                │
┌─────────────────┐  ┌──────────────┐
│ jmcp-transport  │  │ jmcp-jdbc    │
│     -stdio      │  │              │
│ (SPI provider)  │  │ (SPI provider)│
└─────────────────┘  └──────────────┘

     jmcp-client (separate application)
          │
          └─→ requires jmcp-core (protocol & models)


┌─────────────────────────────────────────────────────────────────────────────┐
│                      External Dependencies                                   │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  Third-Party Libraries                                                  │
├─────────────────────────────────────────────────────────────────────────┤
│  • Jackson 3.x (tools.jackson) - JSON serialization                     │
│  • Hibernate Validator 9.x - JSR-380 validation                         │
│  • Expressly 6.x - Expression Language for validation                   │
│  • HikariCP 7.x - Connection pooling                                    │
│  • JSqlParser 5.x - SQL parsing and validation                          │
│  • Log4j2 2.x - Primary logging framework                               │
│  • SLF4J 2.x - Bridged to Log4j2                                        │
│  • JavaFX 25.0.1 - GUI framework (client only)                          │
│                                                                          │
│  Test Dependencies:                                                      │
│  • JUnit 5 (Jupiter) - Testing framework                                 │
│  • Mockito 5.x - Mocking framework                                      │
│  • H2 Database - In-memory database for testing                          │
│  • Byte Buddy - Runtime code generation for Mockito                      │
└─────────────────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                      Key Design Patterns                                     │
└─────────────────────────────────────────────────────────────────────────────┘

1. **Service Provider Interface (SPI)**
   - McpProvider discovered via ServiceLoader
   - TransportProvider discovered via ServiceLoader
   - Zero compile-time coupling between server and providers

2. **Adapter Pattern**
   - JdbcToolAdapter adapts JdbcTool → Tool (core)
   - Resolves domain-specific context (ConnectionContext)

3. **Strategy Pattern**
   - McpProtocolHandler interface with multiple implementations
   - JdbcTool interface with tool-specific implementations
   - Resource interface with resource-specific implementations

4. **Bridge Pattern**
   - ResourceProxyTool bridges resources → tools API for resource-unaware clients

5. **HashMap Dispatch**
   - McpServer uses getSupportedMethods() → HashMap for O(1) method routing
   - ToolsHandler uses HashMap index for O(1) tool lookup

6. **Immutable Records**
   - All model classes are Java records
   - Thread-safe by default

7. **Validation Pipeline**
   - JSR-380 annotations on model records → McpValidator
   - String pre-checks → JSqlParser AST → fail-safe at SQL layer


┌─────────────────────────────────────────────────────────────────────────────┐
│                      Class Loader Hierarchy                                  │
└─────────────────────────────────────────────────────────────────────────────┘

         ┌────────────────────────┐
         │  Bootstrap ClassLoader │
         └───────────┬────────────┘
                     │
                     ▼
         ┌────────────────────────┐
         │  Platform ClassLoader  │
         └───────────┬────────────┘
                     │
                     ▼
         ┌────────────────────────┐
         │  Application ClassLoader│
         │  (jmcp modules)        │
         └───────────┬────────────┘
                     │
                     ├─────────────────┬─────────────────┐
                     ▼                 ▼                 ▼
         ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
         │ Driver CL #1   │ │ Driver CL #2   │ │ Driver CL #N   │
         │ (PostgreSQL)   │ │ (MySQL)        │ │ (Oracle)       │
         └────────────────┘ └────────────────┘ └────────────────┘

         Each JDBC driver loaded in isolated ClassLoader
         to prevent class conflicts and enable dynamic loading


┌─────────────────────────────────────────────────────────────────────────────┐
│                          Notes                                               │
└─────────────────────────────────────────────────────────────────────────────┘

• All modules are JPMS (Java Platform Module System) modules
• Project uses Java 25
• No Spring Framework - SPI-based discovery, manual dependency management
• Designed for jlink distribution (single executable JVM)
• Read-only database access enforced at multiple layers:
  - String pre-checks for unparseable patterns
  - JSqlParser AST validation (SELECT-only)
  - CTE DML inspection
  - Read-only connections via HikariCP
• All tests use isolated module with "test.org" package prefix
• Test modules are "open" modules for JUnit reflection access
```

## Dependency Count by Layer

| Layer | Classes | Purpose |
|-------|---------|---------|
| **server** | 3 | Entry point, resource proxy tool, server tool provider |
| **core** | 37 | SPI contracts, protocol handlers, models, schema, validation |
| **jdbc** | 50+ | Tools, resources, connection management, driver loading, validation, config |
| **transport-stdio** | 2 | Stdio transport + SPI provider |
| **client** | 19 | JavaFX GUI, services, UI components |
| **Total** | ~110+ | Main implementation classes |

## Module Graph

```
         jmcp-server                    jmcp-client
              │                                 │
              ├─→ jmcp-core (SPI contracts,    ←──┤
              │    protocol & models)              │
              │                                    │
              │    Discovered at runtime via SPI:   │
              │    ├─→ jmcp-transport-stdio         │
              │    └─→ jmcp-jdbc (tools,            │
              │         resources & drivers)         │

         Note: Client and Server are separate applications
               Client uses core models for JSON marshalling
               Server has zero compile-time deps on jdbc/transport
```

---

*Updated: April 14, 2026*

---

## Client Module Dependency Graph

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MCP Client Application                                │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌──────────────────┐
                    │  McpClientApp    │
                    │  (JavaFX App)    │
                    └────┬─────────────┘
                         │
                         │ loads FXML
                         ▼
                ┌─────────────────────┐
                │ McpClientController │
                │ (FXML Controller)   │
                └────┬────────────────┘
                     │
                     │ manages
                     ├──────────────────────────────────┐
                     ▼                                   ▼
              ┌──────────────┐                ┌─────────────────────┐
              │  McpClient   │                │ UI Components       │
              │ (Protocol)   │                │ (ui/ package)       │
              └────┬─────────┘                ├─────────────────────┤
                   │                          │ ToolArgumentFormBuilder│
                   │ uses                     │ ToolListCell          │
                   ├───────────────┐          │ ResourceListCell      │
                   ▼               ▼          │ NavigableResourceView │
      ┌──────────────────┐ ┌──────────────┐  │ NavigableUriDetector  │
      │StdioClient       │ │ Core Models  │  │ ResourceNavHistory    │
      │Transport         │ │(JsonRpcReq.) │  │ ValueParser           │
      │(Process & I/O)   │ │(JsonRpcResp.)│  └─────────────────────┘
      └────┬─────────────┘ │(CallToolRes.)│
           │               │(ListToolsRes)│
           │               │(ListResRes.) │
           │               │(ReadResReq.) │
           │               │(ReadResRes.) │
           │               │(ResourceDesc)│
           │               └──────────────┘
           │
           │ launches & communicates with
           ▼
    ┌───────────────────────┐
    │  MCP Server Process   │
    │  (External)           │
    │  - stdin/stdout       │
    └───────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                        Client Services                                       │
└─────────────────────────────────────────────────────────────────────────────┘

     ┌──────────────────────┐
     │ service/ package     │
     ├──────────────────────┤
     │ McpService           │  Service layer for MCP operations
     │ CommunicationLogger  │  Protocol message logging
     └──────────────────────┘

     ┌──────────────────────┐
     │ Support classes      │
     ├──────────────────────┤
     │ ClientPreferences    │  Persistent preferences (java.prefs)
     │ AccessibilityHelper  │  Accessibility support
     │ CommunicationListener│  Communication event interface
     │ DisplayContent       │  Display content interface
     │ DisplayResult        │  Display result interface
     └──────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                        Client Data Flow                                       │
└─────────────────────────────────────────────────────────────────────────────┘

User Action → Controller Event → McpClient Method → StdioClientTransport
                    ↓                    ↓                    ↓
              Update UI ← Parse Response ← Read JSON ← Server stdout


Example: Connecting to Server
------------------------------
1. User enters command: "./run.sh"
2. User clicks "Connect" button
3. Controller calls: client.connect()
4. McpClient creates: new StdioClientTransport(["./run.sh"])
5. Transport launches: ProcessBuilder.start()
6. McpClient sends: initialize request
7. Server responds: with ServerCapabilities
8. McpClient sends: notifications/initialized
9. Controller calls: client.listTools()
10. Controller updates: tool list in UI


Example: Executing Tool
-----------------------
1. User selects tool from list
2. Controller generates: argument input fields from schema
3. User fills in: argument values
4. User clicks "Execute" button
5. Controller collects: argument map
6. Controller calls: client.callTool(name, args)
7. McpClient sends: tools/call request
8. Server executes: tool and returns result
9. McpClient receives: CallToolResult
10. Controller displays: pretty JSON in results area
```

### Client Module Classes

```
jmcp-client/
├── McpClientApp.java
│   └─ Main JavaFX Application
│       • Loads FXML
│       • Initializes Stage
│       • Entry point: main()
│
├── McpClientController.java
│   └─ FXML Controller
│       • Event handlers (connect, execute, resource browse, etc.)
│       • UI updates
│       • Dynamic form generation
│       • Background thread management
│       • Resource navigation support
│
├── McpClient.java
│   └─ Protocol Handler
│       • connect() - Initialize server
│       • listTools() - Get available tools
│       • callTool() - Execute tool
│       • Resource protocol operations
│       • Uses core models
│
├── StdioClientTransport.java
│   └─ Transport Layer
│       • Process management
│       • stdin/stdout I/O
│       • JSON-RPC send/receive
│       • AutoCloseable
│
├── ClientPreferences.java
│   └─ Preferences (java.prefs)
│       • Persistent server command
│       • User settings
│
├── AccessibilityHelper.java
│   └─ Accessibility Support
│
├── CommunicationListener.java / DisplayContent.java / DisplayResult.java
│   └─ Communication Interfaces
│
├── service/
│   ├── McpService.java
│   │   └─ Service layer for MCP operations
│   └── CommunicationLogger.java
│       └─ Protocol message logging
│
├── ui/
│   ├── ToolArgumentFormBuilder.java
│   │   └─ Dynamic argument forms from JSON Schema
│   ├── ToolListCell.java
│   │   └─ Custom cell renderer for tools
│   ├── ResourceListCell.java
│   │   └─ Custom cell renderer for resources
│   ├── NavigableResourceView.java
│   │   └─ Navigable resource browser
│   ├── NavigableUriDetector.java
│   │   └─ Detects navigable URIs in content
│   ├── ResourceNavigationHistory.java
│   │   └─ Back/forward navigation for resources
│   └── ValueParser.java
│       └─ Smart value parsing (numbers, booleans, JSON, strings)
│
└── McpClient.fxml
    └─ UI Layout
        • Three-panel split pane
        • Tools list + Resource browser
        • Argument forms
        • Results display
```

### Client Module Dependencies

```
External Libraries:
├── JavaFX 25.0.1
│   ├── javafx-controls (UI widgets)
│   └── javafx-fxml (FXML loading)
│
├── Jackson 3.x (via jmcp-core)
│   └── JSON serialization
│
├── Log4j2 (logging)
│
├── java.prefs (preferences)
│
└── jmcp-core
    └── MCP protocol models (tools + resources)
```

### Client-Server Communication

```
Client                          Server
  │                               │
  │ Process.start()               │
  ├──────────────────────────────►│
  │                               │
  │ initialize request            │
  ├──────────────────────────────►│
  │                               │
  │        ServerCapabilities     │
  │◄──────────────────────────────┤
  │                               │
  │ notifications/initialized     │
  ├──────────────────────────────►│
  │                               │
  │ tools/list request            │
  ├──────────────────────────────►│
  │                               │
  │        ListToolsResult        │
  │◄──────────────────────────────┤
  │                               │
  │ resources/list request        │
  ├──────────────────────────────►│
  │                               │
  │        ListResourcesResult    │
  │◄──────────────────────────────┤
  │                               │
  │ tools/call request            │
  ├──────────────────────────────►│
  │                               │
  │        CallToolResult         │
  │◄──────────────────────────────┤
  │                               │
  │ resources/read request        │
  ├──────────────────────────────►│
  │                               │
  │        ReadResourceResult     │
  │◄──────────────────────────────┤
  │                               │
  │ Process.destroy()             │
  ├──────────────────────────────►│
  │                               ✗
```

### Running the Client

```bash
# Via script
./run-client.sh

# Via Maven
mvn -pl jmcp-client javafx:run

# Built and run
java --module-path $JAVAFX_HOME/lib:target/lib \
     --add-modules javafx.controls,javafx.fxml \
     -jar target/jmcp-client-1.0.0-SNAPSHOT.jar
```
