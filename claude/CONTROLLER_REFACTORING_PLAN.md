# McpClientController Refactoring Plan - SOLID Principles

**Date:** December 24, 2025

## Current Issues Analysis

The `McpClientController` class currently violates several SOLID principles:

### Current Responsibilities (Too Many!)

1. **Connection Management** - Connecting/disconnecting from MCP server
2. **Tool Selection & Display** - Managing tool list, selection, sorting
3. **Tool Argument UI Generation** - Building dynamic form fields from schema
4. **Tool Execution** - Executing tools with arguments
5. **Communication Logging** - Formatting and displaying JSON-RPC messages
6. **Preferences Management** - Loading/saving server command
7. **UI State Management** - Enabling/disabling controls
8. **Error Display** - Showing error dialogs
9. **JSON Formatting** - Pretty-printing JSON
10. **Value Parsing** - Converting strings to appropriate types
11. **ListView Cell Rendering** - Custom cell factory for tools

### SOLID Violations

❌ **Single Responsibility Principle (SRP)** - Has 11+ distinct responsibilities  
❌ **Open/Closed Principle (OCP)** - Hard to extend without modifying  
❌ **Interface Segregation Principle (ISP)** - No interfaces, monolithic class  
⚠️ **Dependency Inversion Principle (DIP)** - Depends on concrete classes (partially violated)  
✅ **Liskov Substitution Principle (LSP)** - Not applicable (no inheritance)

## Refactoring Plan

### Phase 1: Extract Service Classes (Business Logic)

#### 1.1 Create `ConnectionService`
**Responsibility:** Manage MCP server connection lifecycle

**Methods:**
- `connect(String[] command, CommunicationListener listener) -> McpClient`
- `disconnect()`
- `isConnected() -> boolean`
- `getServerInfo() -> InitializeResult`

**Benefits:**
- Single responsibility: connection management
- Testable without GUI
- Can be reused in CLI or other interfaces

---

#### 1.2 Create `ToolExecutionService`
**Responsibility:** Execute tools and manage execution state

**Methods:**
- `executeTool(McpClient client, String toolName, Map<String, Object> arguments) -> CallToolResult`
- `listTools(McpClient client) -> List<Tool>`
- `sortTools(List<Tool> tools) -> List<Tool>`

**Benefits:**
- Separates business logic from UI
- Testable independently
- Reusable across different UI implementations

---

#### 1.3 Create `CommunicationLogger`
**Responsibility:** Format and log communication events

**Methods:**
- `logRequest(JsonRpcRequest request)`
- `logResponse(JsonRpcResponse response)`
- `logError(String message, Exception exception)`
- `getFormattedLog() -> String`
- `clear()`

**Benefits:**
- Single responsibility: logging
- Can change log format without touching controller
- Could easily add file logging, log levels, etc.

---

### Phase 2: Extract UI Helper Classes

#### 2.1 Create `ToolListCellFactory`
**Responsibility:** Provide custom cell rendering for tool list

**Implementation:**
```java
public class ToolListCellFactory implements Callback<ListView<Tool>, ListCell<Tool>> {
    @Override
    public ListCell<Tool> call(ListView<Tool> param) {
        return new ToolListCell();
    }
    
    private static class ToolListCell extends ListCell<Tool> {
        @Override
        protected void updateItem(Tool tool, boolean empty) {
            // Current cell rendering logic
        }
    }
}
```

**Benefits:**
- Separates rendering logic
- Reusable across multiple views
- Easier to test cell rendering

---

#### 2.2 Create `ToolArgumentFormBuilder`
**Responsibility:** Build argument input forms from JSON schema

**Methods:**
- `buildForm(Tool tool, VBox container) -> Map<String, TextField>`
- `clearForm(VBox container)`
- `collectArguments(Map<String, TextField> fields) -> Map<String, Object>`

**Benefits:**
- Complex schema-to-UI logic isolated
- Can enhance with better controls (Spinner, ComboBox, etc.)
- Testable independently

---

#### 2.3 Create `ValueParser`
**Responsibility:** Parse string values to appropriate types

**Methods:**
- `parse(String value) -> Object`
- `parseNumber(String value) -> Number`
- `parseBoolean(String value) -> Boolean`
- `parseJson(String value) -> Object`

**Benefits:**
- Single responsibility: type conversion
- Easy to add new types
- Testable without GUI

---

### Phase 3: Extract View Models / Presenters

#### 3.1 Create `ConnectionViewModel`
**Responsibility:** Manage connection-related state and actions

**State:**
- `serverCommand: String`
- `connectionStatus: ConnectionStatus`
- `serverInfo: String`

**Methods:**
- `connect()`
- `disconnect()`
- `saveCommand()`

**Benefits:**
- Separates state from view
- Easier to test state transitions
- Could implement Observable pattern for reactive UI

---

#### 3.2 Create `ToolExecutionViewModel`
**Responsibility:** Manage tool execution state and actions

**State:**
- `selectedTool: Tool`
- `executionInProgress: boolean`
- `lastResult: CallToolResult`

**Methods:**
- `selectTool(Tool tool)`
- `executeTool(Map<String, Object> arguments)`
- `clearResult()`

**Benefits:**
- Separates execution state from UI
- Testable business logic
- Clear state management

---

### Phase 4: Refactor Controller to Coordinator

#### 4.1 Slim Down `McpClientController`
**New Responsibility:** Coordinate between services and update UI

**Dependencies (Injected):**
- `ConnectionService`
- `ToolExecutionService`
- `CommunicationLogger`
- `ToolListCellFactory`
- `ToolArgumentFormBuilder`
- `ValueParser`
- `ClientPreferences`

**Remaining Methods:**
- `initialize()` - Wire up UI components
- `setupPreferences()` - Load preferences into UI
- `onConnect()` - Delegate to ConnectionService, update UI
- `onDisconnect()` - Delegate to ConnectionService, update UI
- `onToolSelected()` - Delegate to ToolArgumentFormBuilder, update UI
- `onExecute()` - Delegate to ToolExecutionService, update UI
- `updateConnectionState()` - Update UI controls
- `showError()` - Display error dialog

**Benefits:**
- Controller focused on coordination
- Thin layer between UI and business logic
- Clear separation of concerns

---

## Class Hierarchy After Refactoring

```
jmcp-client/src/main/java/org/peacetalk/jmcp/client/

├── service/
│   ├── ConnectionService.java          (NEW - connection lifecycle)
│   ├── ToolExecutionService.java       (NEW - tool operations)
│   └── CommunicationLogger.java        (NEW - logging logic)
│
├── ui/
│   ├── ToolListCellFactory.java        (NEW - custom cell rendering)
│   ├── ToolArgumentFormBuilder.java    (NEW - dynamic form generation)
│   └── ValueParser.java                (NEW - type conversion)
│
├── viewmodel/  (OPTIONAL - could be Phase 5)
│   ├── ConnectionViewModel.java        (FUTURE - connection state)
│   └── ToolExecutionViewModel.java     (FUTURE - execution state)
│
└── McpClientController.java            (REFACTORED - coordination only)
    ClientPreferences.java               (EXISTING - already extracted)
    McpClient.java                       (EXISTING)
    CommunicationListener.java           (EXISTING)
```

---

## SOLID Principles Achieved

### ✅ Single Responsibility Principle (SRP)
Each class has one reason to change:
- `ConnectionService` - connection logic changes
- `ToolExecutionService` - execution logic changes
- `CommunicationLogger` - logging format changes
- `ToolListCellFactory` - cell rendering changes
- `ToolArgumentFormBuilder` - form generation changes
- `ValueParser` - parsing logic changes
- `McpClientController` - UI coordination changes

### ✅ Open/Closed Principle (OCP)
- Can extend `ValueParser` with new types without modifying
- Can add new logging destinations to `CommunicationLogger`
- Can add new cell renderers without changing controller

### ✅ Interface Segregation Principle (ISP)
Create interfaces for each service:
```java
interface IConnectionService { ... }
interface IToolExecutionService { ... }
interface ICommunicationLogger { ... }
interface IValueParser { ... }
```
Controller depends on interfaces, not concrete classes.

### ✅ Dependency Inversion Principle (DIP)
- Controller depends on abstractions (interfaces)
- Services depend on abstractions
- High-level modules don't depend on low-level modules

---

## Implementation Order

### Phase 1: Low-Hanging Fruit (Quick Wins)
1. **Extract `ValueParser`** (30 min)
   - Simple, no dependencies
   - Immediate benefit
   - Easy to test

2. **Extract `ToolListCellFactory`** (30 min)
   - Self-contained
   - Removes inline anonymous class
   - Cleaner controller

3. **Extract `CommunicationLogger`** (1 hour)
   - Clear boundary
   - Two methods to extract
   - Testable

### Phase 2: Service Extraction (Medium Effort)
4. **Extract `ToolArgumentFormBuilder`** (2 hours)
   - More complex
   - Schema parsing logic
   - UI generation

5. **Extract `ConnectionService`** (2 hours)
   - Thread management
   - Listener setup
   - State management

6. **Extract `ToolExecutionService`** (1 hour)
   - Simpler than connection
   - Clear boundaries

### Phase 3: Controller Refactoring (Final Polish)
7. **Refactor `McpClientController`** (2 hours)
   - Wire up all services
   - Remove old code
   - Test integration

8. **Create Service Interfaces** (1 hour)
   - Define contracts
   - Update dependencies
   - Enable mocking

### Phase 4: Optional Enhancements
9. **Add ViewModels** (Optional - 4 hours)
   - Observable state
   - Reactive UI updates
   - Advanced state management

---

## Testing Strategy

### Unit Tests (NEW - Easy with Refactoring)

```java
// Service Tests
ConnectionServiceTest - Test connection logic
ToolExecutionServiceTest - Test execution logic
CommunicationLoggerTest - Test log formatting
ValueParserTest - Test type parsing
ToolArgumentFormBuilderTest - Test form generation

// Integration Tests
McpClientControllerTest - Test UI coordination (with mocks)
```

### Benefits:
- Services testable without JavaFX
- Mock services for controller tests
- Fast, isolated tests

---

## Migration Strategy

### Approach: Strangler Fig Pattern
Don't rewrite everything at once. Gradually extract classes while keeping the application working.

**Steps:**
1. Extract one class at a time
2. Run existing application after each extraction
3. Verify everything still works
4. Commit after each successful extraction
5. Write tests for extracted classes

### Backward Compatibility
- Keep `McpClientController` working throughout
- Extract without breaking existing functionality
- FXML doesn't need to change
- No changes to `McpClientApp`

---

## Estimated Effort

| Phase | Time | Difficulty |
|-------|------|------------|
| Phase 1 (3 classes) | 2 hours | Easy |
| Phase 2 (3 services) | 5 hours | Medium |
| Phase 3 (Controller) | 3 hours | Medium |
| Phase 4 (Interfaces) | 1 hour | Easy |
| Testing | 4 hours | Medium |
| **TOTAL** | **15 hours** | **Medium** |

Phase 4 (ViewModels) - Optional: +4 hours

---

## Benefits Summary

### Code Quality
- ✅ Smaller, focused classes (~100 lines each vs. 418 lines)
- ✅ Each class has single responsibility
- ✅ Easier to understand and maintain
- ✅ Easier to test (services don't need JavaFX)

### Maintainability
- ✅ Changes isolated to specific classes
- ✅ Less risk of breaking unrelated functionality
- ✅ Easier to find and fix bugs

### Extensibility
- ✅ Easy to add new value types
- ✅ Easy to add new logging formats
- ✅ Easy to add new UI controls based on schema
- ✅ Could reuse services in CLI version

### Testability
- ✅ Unit test services without GUI
- ✅ Mock services in controller tests
- ✅ Fast, isolated tests
- ✅ Higher test coverage possible

---

## Risks & Mitigation

### Risk 1: Breaking Existing Functionality
**Mitigation:** 
- Extract one class at a time
- Test after each extraction
- Keep application running throughout

### Risk 2: Over-Engineering
**Mitigation:**
- Start with Phase 1 & 2 only
- Assess if Phase 3 & 4 are needed
- Don't add ViewModels unless truly beneficial

### Risk 3: Increased Complexity
**Mitigation:**
- Keep services simple and focused
- Good naming conventions
- Clear documentation
- Package organization

---

## Recommendation

**Start with Phase 1 (3 classes)**

1. `ValueParser` - Quick win, immediate benefit
2. `ToolListCellFactory` - Clean up inline class
3. `CommunicationLogger` - Clear separation

**This alone will:**
- Reduce controller from 418 to ~300 lines
- Make code more testable
- Demonstrate the value of refactoring
- Build momentum for further improvements

**Then assess:** Do we need Phase 2? The quick wins might be sufficient for now.

---

## Alternative: Minimal Refactoring

If full refactoring is too much, consider **minimal extraction:**

1. Extract `ValueParser` only
2. Extract `CommunicationLogger` only
3. Extract `ToolListCellFactory` only

**Result:** 
- Controller drops to ~300 lines
- Most complex logic extracted
- 80% of benefit with 20% of effort
- Still a significant improvement

---

## Conclusion

The current `McpClientController` violates SRP with 11+ responsibilities. The proposed refactoring extracts 6-8 focused classes, each with a single responsibility. This improves:

- **Maintainability** - Easier to understand and modify
- **Testability** - Can unit test services without JavaFX
- **Extensibility** - Easy to add new features
- **Code Quality** - Follows SOLID principles

**Recommended Approach:** Incremental extraction using Strangler Fig pattern, starting with Phase 1 quick wins.

