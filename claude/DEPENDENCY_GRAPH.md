# JDBC MCP Server - Dependency Graph

## ASCII Art Dependency Graph

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                    Main                                     │
│                        (jmcp-server/Main.java)                            │
└────────────────┬────────────────────────────┬────────────────┬──────────────┘
                 │                            │                │
                 ▼                            ▼                ▼
        ┌────────────────┐         ┌─────────────────┐   ┌──────────────────┐
        │  McpServer     │         │ StdioTransport  │   │ Configuration    │
        │  (core)        │         │ (transport)     │   │ (server)         │
        └────┬───────────┘         └─────────────────┘   └──────────────────┘
             │                              │
             │ implements                   │ implements
             ▼                              ▼
   ┌──────────────────────┐      ┌──────────────────────┐
   │ McpRequestHandler    │      │  McpTransport        │
   │ (core/transport)     │      │  (core/transport)    │
   └──────────────────────┘      └──────────────────────┘
             │
             │ registers handlers
             ├─────────────────────┬──────────────────────┐
             ▼                     ▼                      ▼
   ┌───────────────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │ InitializationHandler │  | JdbcToolsHandler │  │ [Other Handlers] │
   │ (core/protocol)       │  │ (jdbc)           │  │ (future)         │
   └────┬──────────────────┘  └────┬─────────────┘  └──────────────────┘
        │                          │
        │ implements               │ implements
        ▼                          ▼
   ┌──────────────────────────────────────┐
   │    McpProtocolHandler                │
   │    (core/protocol)                   │
   └──────────────────────────────────────┘
        │
        │ uses models
        ▼
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
   │  • Tool                              │
   │  • CallToolRequest                   │
   │  • CallToolResult                    │
   │  • ListToolsResult                   │
   │  • Content                           │
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
│                        JDBC Tools Handler Chain                              │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌──────────────────┐
                    │ JdbcToolsHandler │
                    │ (jdbc)           │
                    └────┬─────────────┘
                         │
                         │ manages
                         ├──────────────┬──────────────┐
                         ▼              ▼              ▼
               ┌──────────────┐ ┌───────────────────┐ ┌───────────────────┐
               │ JdbcTool[]   │ │ ConnectionContext │ │ ConnectionManager │
               │ (interface)  │ │ (jdbc)            │ │ (jdbc)            │
               └──────┬───────┘ └───────────────────┘ └────┬──────────────┘
                      │                                     │
                      │ implemented by                      │ uses
                      ├─────────┬──────────┬───────────────┤
                      ▼         ▼          ▼               ▼
            ┌─────────────┐ ┌────────────────┐ ┌──────────────────┐ ┌───────────────────┐
            │ QueryTool   │ │ ListTablesTool │ │DescribeTableTool │ │ JdbcDriverManager │
            │ (jdbc/tools)│ │ (jdbc/tools)   │ │ (jdbc/tools)     │ │ (jdbc/driver)     │
            └─────────────┘ └────────────────┘ └──────────────────┘ └──────┬────────────┘
            ┌─────────────────┐ ┌────────────────┐ ┌────────────────┐      │
            │PreviewTableTool │ │ListSchemasTool │ │GetRowCountTool │      │ manages
            │ (jdbc/tools)    │ │ (jdbc/tools)   │ │ (jdbc/tools)   │      ▼
            └─────────────────┘ └────────────────┘ └────────────────┘ ┌──────────────────┐
                      │                                               │ DriverCoordinates│
                      │ returns                                       │ (jdbc/driver)    │
                      ▼                                               └──────────────────┘
            ┌─────────────────────────────────┐                             │
            │    Tool Result Records          │                             │ loads
            │    (jdbc/tools/results)         │                             ▼
            ├─────────────────────────────────┤                    ┌─────────────────┐
            │  • QueryResult                  │                    │ JDBC Drivers    │
            │  • TableDescription             │                    │ (Maven Central) │
            │  • ColumnMetadata               │                    │ - H2            │
            │  • IndexInfo                    │                    │ - PostgreSQL    │
            │  • TablePreviewResult           │                    │ - MySQL         │
            │  • TablesListResult             │                    │ - Oracle        │
            │  • TableInfo                    │                    │ - SQL Server    │
            │  • SchemasListResult            │                    │ - Derby         │
            │  • SchemaInfo                   │                    │ - SQLite        │
            │  • RowCountResult               │                    └─────────────────┘
            └─────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                        Tool Schema & Validation                             │
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
    │    (jdbc/schema)             │
    ├──────────────────────────────┤
    │  • ObjectSchema              │
    │  • StringProperty            │
    │  • IntegerProperty           │
    │  • ArrayProperty             │
    └────┬─────────────────────────┘
         │
         │ validated by
         ▼
    ┌──────────────────────────────┐
    │  ReadOnlySqlValidator        │
    │  (jdbc/validation)           │
    │  Uses: JSqlParser            │
    └──────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                          Data Flow Summary                                  │
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
4. McpServer finds appropriate McpProtocolHandler
                 │
                 ├─ initialize → InitializationHandler
                 │                      │
                 │                      └─ returns ServerCapabilities
                 │
                 ├─ tools/list → JdbcToolsHandler
                 │                      │
                 │                      └─ returns ListToolsResult
                 │
                 └─ tools/call → JdbcToolsHandler
                                        │
                                        ├─ parses CallToolRequest
                                        │
                                        ├─ finds JdbcTool by name
                                        │
                                        ├─ validates SQL (if applicable)
                                        │
                                        ├─ executes tool with ConnectionContext
                                        │
                                        └─ returns CallToolResult
                 │
                 ▼
5. McpServer serializes JsonRpcResponse
                 │
                 ▼
6. StdioTransport writes response to stdout


┌─────────────────────────────────────────────────────────────────────────────┐
│                          Module Dependencies                                │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  jmcp-server   │
│  (Main entry)    │
└────┬─────────────┘
     │
     │ depends on
     ├──────────────────┬──────────────────┬──────────────────┐
     ▼                  ▼                  ▼                  ▼
┌────────────┐  ┌─────────────────┐  ┌──────────────┐  ┌──────────────────┐
│ jmcp-core│  │ jmcp-transport│  │ jmcp-jdbc  │  │jmcp-driver-mgr │
│            │  │     -stdio      │  │              │  │                  │
└────────────┘  └─────────────────┘  └──────┬───────┘  └──────────────────┘
     ▲                  ▲                   │                  ▲
     │                  │                   └──────────────────┘
     └──────────────────┴───────────────────┘
            (all modules depend on core)


┌─────────────────────────────────────────────────────────────────────────────┐
│                      External Dependencies                                  │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  Third-Party Libraries                                                  │
├─────────────────────────────────────────────────────────────────────────┤
│  • Jackson (tools.jackson) - JSON serialization                 │
│  • Hibernate Validator - JSR-380 validation                             │
│  • Expressly - Expression Language for validation                       │
│  • HikariCP - Connection pooling                                        │
│  • JSqlParser - SQL parsing and validation                              │
│  • jdbctl - SQL query utilities                                         │
│  • SLF4J - Logging facade                                               │
│                                                                         │
│  Test Dependencies:                                                     │
│  • JUnit 5 (Jupiter) - Testing framework                                │
│  • Mockito 5.x - Mocking framework                                      │
│  • H2 Database - In-memory database for testing                         │
│  • NetworkNT JSON Schema Validator - Schema validation testing          │
└─────────────────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                      Key Design Patterns                                    │
└─────────────────────────────────────────────────────────────────────────────┘

1. **Strategy Pattern**
   - McpProtocolHandler interface with multiple implementations
   - JdbcTool interface with tool-specific implementations

2. **Chain of Responsibility**
   - McpServer dispatches to first handler that canHandle() the request

3. **Factory Pattern**
   - JdbcDriverManager creates isolated ClassLoaders for JDBC drivers
   - ConnectionManager creates connection pools

4. **Dependency Injection**
   - Manual DI in Main (no Spring)
   - Handlers registered with McpServer
   - Tools registered with JdbcToolsHandler

5. **Immutable Records**
   - All model classes are Java records
   - Thread-safe by default

6. **Validation Pipeline**
   - JSR-380 annotations on model records
   - McpValidator validates at protocol layer
   - ReadOnlySqlValidator validates at SQL layer


┌─────────────────────────────────────────────────────────────────────────────┐
│                      Class Loader Hierarchy                                 │
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
         │  (jmcp modules)      │
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
│                          Notes                                              │
└─────────────────────────────────────────────────────────────────────────────┘

• All modules are JPMS (Java Platform Module System) modules
• Project uses Java 25 features
• No Spring Framework - manual dependency management
• Designed for jlink distribution (single executable JVM)
• Read-only database access enforced at multiple layers:
  - SQL validation via JSqlParser
  - Read-only connections via HikariCP
  - Transaction rollback on connection close
• All tests use isolated module with "test.org" package prefix
• Test modules are "open" modules for JUnit reflection access
```

## Dependency Count by Layer

| Layer | Classes | Purpose |
|-------|---------|---------|
| **server** | 3 | Entry point, configuration |
| **core** | 13 | Protocol models, handlers, validation |
| **jdbc** | 26 | Tools, connection management, SQL validation, driver loading |
| **transport-stdio** | 1 | Stdio transport implementation |
| **client** | 4 | JavaFX GUI client for MCP servers |
| **Total** | ~47 | Main implementation classes |

## Module Graph

```
         jmcp-server                    jmcp-client
              │                                 │
              ├─→ jmcp-core (protocol & models) ←──┤
              ├─→ jmcp-transport-stdio (I/O)       │
              └─→ jmcp-jdbc (database tools        │
                   & driver management) ◄─────────────┘
         
         Note: Client and Server are separate applications
               Client uses core models for JSON marshalling
               JDBC module includes driver loading
```

---

*Generated: November 28, 2025*

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
                     ▼
              ┌──────────────┐
              │  McpClient   │
              │ (Protocol)   │
              └────┬─────────┘
                   │
                   │ uses
                   ├─────────────────┬────────────────┐
                   ▼                 ▼                ▼
      ┌──────────────────┐ ┌──────────────┐ ┌────────────────┐
      │StdioClientTransport │ │ Core Models  │ │ Jackson MAPPER │
      │(Process & I/O)    │ │(JsonRpcRequest)│ │(Serialization) │
      └────┬─────────────┘ │(JsonRpcResponse)│ └────────────────┘
           │               │(CallToolResult) │
           │               │(ListToolsResult)│
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
│                        Client Data Flow                                      │
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
│       • Event handlers (connect, execute, etc.)
│       • UI updates
│       • Dynamic form generation
│       • Background thread management
│
├── McpClient.java
│   └─ Protocol Handler
│       • connect() - Initialize server
│       • listTools() - Get available tools
│       • callTool() - Execute tool
│       • Uses core models
│
├── StdioClientTransport.java
│   └─ Transport Layer
│       • Process management
│       • stdin/stdout I/O
│       • JSON-RPC send/receive
│       • AutoCloseable
│
└── McpClient.fxml
    └─ UI Layout
        • Three-panel split pane
        • Tools list
        • Argument forms
        • Results display
```

### Client Module Dependencies

```
External Libraries:
├── JavaFX 23.0.1
│   ├── javafx-controls (UI widgets)
│   └── javafx-fxml (FXML loading)
│
├── Jackson (via jmcp-core)
│   └── JSON serialization
│
└── jmcp-core
    └── MCP protocol models
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
  │ tools/call request            │
  ├──────────────────────────────►│
  │                               │
  │        CallToolResult         │
  │◄──────────────────────────────┤
  │                               │
  │ Process.destroy()             │
  ├──────────────────────────────►│
  │                               ✗
```

### Client Architecture Highlights

1. **Separation of Concerns**
   - McpClientApp: JavaFX application lifecycle
   - McpClientController: UI logic and event handling
   - McpClient: MCP protocol implementation
   - StdioClientTransport: Process and I/O management

2. **Asynchronous Operations**
   - Connection in background thread
   - Tool execution in background thread
   - UI updates via Platform.runLater()

3. **Dynamic UI Generation**
   - Argument forms generated from JSON Schema
   - Type detection for input values
   - Automatic layout based on tool definition

4. **Reusable Core Models**
   - Client uses same models as server
   - Ensures protocol compatibility
   - Jackson handles serialization

5. **Process Management**
   - Launches server as child process
   - Manages stdin/stdout communication
   - Proper cleanup on close

### Running the Client

```bash
# Via script
./run-client.sh

# Via Maven
mvn -pl jmcp-client javafx:run

# Built and run
java --module-path $JAVAFX_HOME/lib:target/lib \
     --add-modules javafx.controls,javafx.fxml \
     -jar target/jmcp-client-1.0-SNAPSHOT.jar
```


