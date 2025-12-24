# Communication Log Feature

**Date:** December 23, 2025

## Overview

Added a Communication Log feature to the MCP Client GUI that displays all JSON-RPC messages exchanged between the client and server. This provides visibility into the protocol communication for debugging and understanding.

## Implementation

### User Interface

**Location:** Tabbed interface in the right panel

**Two Tabs:**
1. **Result** - Shows tool execution results (existing functionality)
2. **Communication Log** - Shows all JSON-RPC communication

### Visual Design

The communication log uses visual separators and formatting to make it easy to follow:

```
================================================================================
>>> SENT initialize <<<
================================================================================
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "method" : "initialize",
  "params" : {
    "protocolVersion" : "2024-11-05",
    ...
  }
}

================================================================================
>>> RECEIVED Response <<<
================================================================================
{
  "jsonrpc" : "2.0",
  "id" : 1,
  "result" : {
    "protocolVersion" : "2024-11-05",
    ...
  }
}

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
>>> ERROR <<<
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
Connection failed
Exception: IOException
Message: Server closed connection
```

### Features

1. **Monospace font** - Fixed-width font for JSON readability
2. **Pretty-printed JSON** - Indented, formatted JSON for easy reading
3. **Visual separators** - Clear boundaries between messages
4. **Direction indicators** - "SENT" vs "RECEIVED" labels
5. **Method names** - Shows the RPC method name in headers
6. **Error logging** - Errors inserted at the appropriate location
7. **Scrollable** - Auto-scrolls to show latest messages
8. **Non-editable** - Read-only view

## Files Modified

### 1. McpClient.fxml

Changed the right panel from a single TextArea to a TabPane:

```xml
<TabPane VBox.vgrow="ALWAYS" tabClosingPolicy="UNAVAILABLE">
    <Tab text="Result">
        <TextArea fx:id="resultArea" editable="false" wrapText="true">
            <promptText>Tool results will appear here...</promptText>
        </TextArea>
    </Tab>
    <Tab text="Communication Log">
        <TextArea fx:id="communicationLogArea" editable="false" wrapText="false"
                 style="-fx-font-family: 'Monospace', 'Consolas', 'Courier New', monospace;">
            <promptText>JSON-RPC communication will appear here...</promptText>
        </TextArea>
    </Tab>
</TabPane>
```

**Key attributes:**
- `tabClosingPolicy="UNAVAILABLE"` - Tabs cannot be closed
- `wrapText="false"` - JSON doesn't wrap (maintains formatting)
- Monospace font family for code readability

### 2. StdioClientTransport.java

Added a listener interface for communication events:

```java
public interface CommunicationListener {
    void onRequestSent(JsonRpcRequest request);
    void onResponseReceived(JsonRpcResponse response);
    void onError(String message, Exception exception);
}
```

Modified `sendRequest()` to notify listeners:
- Before sending: `listener.onRequestSent(request)`
- After receiving: `listener.onResponseReceived(response)`
- On error: `listener.onError(message, exception)`

### 3. McpClient.java

Added method to set communication listener:

```java
public void setCommunicationListener(StdioClientTransport.CommunicationListener listener) {
    transport.setListener(listener);
}
```

### 4. McpClientController.java

**Added field:**
```java
@FXML private TextArea communicationLogArea;
```

**Added logging methods:**
- `logCommunication(direction, type, content)` - Logs requests/responses
- `logError(message, exception)` - Logs errors

**Set up listener in onConnect():**
```java
client.setCommunicationListener(new StdioClientTransport.CommunicationListener() {
    @Override
    public void onRequestSent(JsonRpcRequest request) {
        logCommunication("SENT", request.method(), request);
    }

    @Override
    public void onResponseReceived(JsonRpcResponse response) {
        logCommunication("RECEIVED", "Response", response);
    }

    @Override
    public void onError(String message, Exception exception) {
        logError(message, exception);
    }
});
```

**Clear log on disconnect:**
```java
communicationLogArea.clear();
```

## Message Format

### Request Messages

```
================================================================================
>>> SENT <method-name> <<<
================================================================================
{
  "jsonrpc" : "2.0",
  "id" : <request-id>,
  "method" : "<method-name>",
  "params" : { ... }
}
```

### Response Messages

```
================================================================================
>>> RECEIVED Response <<<
================================================================================
{
  "jsonrpc" : "2.0",
  "id" : <request-id>,
  "result" : { ... }
}
```

### Error Messages

```
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
>>> ERROR <<<
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
<error-message>
Exception: <exception-class>
Message: <exception-message>
```

## Use Cases

### 1. Debugging Connection Issues

See exactly what initialize request was sent and what response was received:
- Protocol version mismatches
- Capability negotiation
- Server info

### 2. Understanding Tool Execution

See the exact parameters sent to tools and full results:
- Verify parameters are correct
- See complete results (not just summary)
- Debug tool call failures

### 3. Learning the MCP Protocol

Educational tool for understanding JSON-RPC and MCP:
- See request/response pairs
- Understand message structure
- Learn protocol flow

### 4. Troubleshooting Errors

Errors appear inline at the point they occurred:
- Connection failures
- Parse errors
- Protocol errors
- Server crashes

## Message Flow Example

Typical connection sequence:

```
SENT initialize →
← RECEIVED Response (initialize result)
SENT notifications/initialized →
SENT tools/list →
← RECEIVED Response (tools list)
```

Tool execution:

```
SENT tools/call →
← RECEIVED Response (call result)
```

Error during execution:

```
SENT tools/call →
ERROR: Server closed connection
```

## Technical Details

### Threading

All logging happens on the JavaFX Application Thread:
- Uses `Platform.runLater()` to ensure thread safety
- Appends to TextArea safely from background threads
- No UI freezing

### Memory Considerations

- Log grows unbounded during session
- Cleared on disconnect
- For long sessions, could potentially use large memory
- Future enhancement: Add log size limit or clear button

### JSON Formatting

Uses Jackson's pretty printer:
```java
MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(content)
```

This provides:
- 2-space indentation
- Field alignment
- Readable structure

## User Experience

### Workflow

1. **Connect to server** - See initialize handshake in log
2. **Select tool** - No log entries (just UI interaction)
3. **Execute tool** - See:
   - SENT tools/call request
   - RECEIVED response
   - Result also appears in Result tab
4. **Switch to Log tab** - Review all communication
5. **Disconnect** - Log is cleared

### Benefits

1. **Transparency** - See exactly what's happening
2. **Debugging** - Troubleshoot protocol issues
3. **Learning** - Understand MCP protocol
4. **Trust** - Verify data being sent
5. **Audit** - Record of session activity

## Alternative Designs Considered

### 1. Toggle View (Result ↔ Log)

**Pros:** Saves screen space
**Cons:** Can't see both at once, requires toggling
**Not used:** Tabs allow viewing either without losing access to both

### 2. Popup Window

**Pros:** Can position separately
**Cons:** Extra window management, easy to lose
**Not used:** Integrated tabs are more convenient

### 3. Bottom Panel Below Results

**Pros:** Can see both simultaneously
**Cons:** Reduces vertical space for both
**Not used:** Tabs provide full vertical space for each

### 4. Collapsible Log Viewer

**Pros:** Can hide when not needed
**Cons:** Requires animation, complex UI
**Not used:** Tabs are simpler

## Future Enhancements

Potential improvements:

1. **Log Filtering**
   - Filter by message type (request/response/error)
   - Filter by method name
   - Search in log

2. **Log Management**
   - Clear log button
   - Save log to file
   - Copy selected entries
   - Max size limit with auto-truncation

3. **Syntax Highlighting**
   - Color-code JSON
   - Highlight errors in red
   - Syntax-aware formatting

4. **Collapsible Entries**
   - Collapse/expand individual messages
   - Collapse all/expand all
   - Show summary line when collapsed

5. **Message Pairing**
   - Link requests to their responses
   - Highlight paired messages
   - Show request when viewing response

6. **Timestamps**
   - Add timestamp to each message
   - Show time elapsed between request/response
   - Performance metrics

7. **Export Capabilities**
   - Export to JSON file
   - Export to text file
   - Share diagnostic logs

## Testing

1. Run client: `./run-client.sh`
2. Click "Communication Log" tab - should be empty
3. Connect to server
4. **Verify:** See initialize request/response
5. **Verify:** See tools/list request/response
6. Select a tool and execute it
7. **Verify:** See tools/call request/response in log
8. **Verify:** Result also appears in Result tab
9. Switch between tabs
10. **Verify:** Can access both views
11. Disconnect
12. **Verify:** Log is cleared

## Benefits Summary

✅ **Visual separation** - Clear boundaries between messages  
✅ **Monospace font** - Easy to read JSON  
✅ **Pretty printing** - Well-formatted, indented JSON  
✅ **Direction labels** - Clear SENT vs RECEIVED  
✅ **Error visibility** - Errors shown inline  
✅ **Non-intrusive** - Optional tab, doesn't interfere with results  
✅ **Educational** - Learn the MCP protocol  
✅ **Debugging** - Troubleshoot issues effectively  

## Code Statistics

**Lines added:** ~100  
**Files modified:** 4  
**New interfaces:** 1 (CommunicationListener)  
**New methods:** 2 (logCommunication, logError)  
**New UI components:** 1 (TabPane with 2 tabs)  

## References

- [JavaFX TabPane](https://openjfx.io/javadoc/17/javafx.controls/javafx/scene/control/TabPane.html)
- [Jackson Pretty Printing](https://github.com/FasterXML/jackson-databind)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [MCP Protocol Specification](https://spec.modelcontextprotocol.io/)

