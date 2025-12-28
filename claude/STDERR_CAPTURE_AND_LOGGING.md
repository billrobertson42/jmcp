# Exception Stack Trace Logging and Server Stderr Capture

**Date:** December 24, 2025

## Summary

Implemented comprehensive error logging throughout the codebase with stack traces printed to `System.err`, and added a "Server Stderr" tab to the client GUI to display server error output in real-time.

## Changes Made

### 1. Server-Side Stack Trace Logging

All exception handlers throughout the server codebase now print stack traces to `System.err` for better debugging.

#### JdbcToolsHandler.java
- Added stack trace printing in `handle()` method exception handler
- Added stack trace printing in `handleCallTool()` method exception handler

```java
catch (Exception e) {
    System.err.println("Error handling request: " + request.method());
    e.printStackTrace(System.err);
    return JsonRpcResponse.error(request.id(), JsonRpcError.internalError(e.getMessage()));
}
```

#### McpServer.java
- Added stack trace printing in `handleRequest()` for parse errors
- Added stack trace printing in `processRequest()` for handler errors

```java
catch (Exception e) {
    System.err.println("Failed to parse request: " + e.getMessage());
    e.printStackTrace(System.err);
    // ...error response
}
```

#### Main.java (Server)
- Added stack trace printing in shutdown hook exception handler

```java
catch (Exception e) {
    System.err.println("Error during shutdown: " + e.getMessage());
    e.printStackTrace(System.err);
}
```

### 2. Client GUI - Server Stderr Tab

Added a third tab to the Output section to display server stderr in real-time.

#### McpClient.fxml
Added new "Server Stderr" tab after "Communication Log":

```xml
<Tab text="Server Stderr">
    <TextArea fx:id="serverStderrArea" editable="false" wrapText="true"
             style="-fx-font-family: 'Monospace', 'Consolas', 'Courier New', monospace;">
        <promptText>Server error output will appear here...</promptText>
    </TextArea>
</Tab>
```

**Tab Layout:**
1. **Result** - Tool execution results
2. **Communication Log** - JSON-RPC request/response log
3. **Server Stderr** - Server error output ← NEW

#### McpClientController.java

**Added field:**
```java
@FXML private TextArea serverStderrArea;
```

**Added method to append stderr:**
```java
private void appendToServerStderr(String line) {
    Platform.runLater(() -> {
        serverStderrArea.appendText(line + "\n");
        serverStderrArea.setScrollTop(Double.MAX_VALUE);  // Auto-scroll
    });
}
```

**Updated onDisconnect():**
- Now clears `serverStderrArea` on disconnect

**Updated listener:**
- Added `onServerStderr()` implementation to append lines to stderr tab

### 3. Transport Layer - Stderr Capture

#### CommunicationListener.java
Added new method to interface:

```java
void onServerStderr(String line);
```

All implementations must now handle stderr events.

#### StdioClientTransport.java

**Key Changes:**

1. **Capture stderr instead of inheriting it:**
```java
ProcessBuilder builder = new ProcessBuilder(command);
builder.redirectError(ProcessBuilder.Redirect.PIPE);  // Changed from INHERIT
```

2. **Added stderr reader:**
```java
private BufferedReader stderrReader;
private Thread stderrReaderThread;
```

3. **Start stderr reader thread:**
```java
private void startStderrReader() {
    stderrReaderThread = new Thread(() -> {
        try {
            String line;
            while ((line = stderrReader.readLine()) != null) {
                String stderrLine = line;
                listeners.forEach(l -> notifyStderr(l, stderrLine));
            }
        } catch (IOException e) {
            // Server process ended or stream closed
        }
    }, "stderr-reader");
    stderrReaderThread.setDaemon(true);
    stderrReaderThread.start();
}
```

4. **Notify listeners:**
```java
private static void notifyStderr(CommunicationListener listener, String line) {
    try {
        listener.onServerStderr(line);
    } catch (Exception e) {
        System.err.println("Listener error on stderr: " + e.getMessage());
    }
}
```

5. **Cleanup on close:**
- Close `stderrReader`
- Interrupt `stderrReaderThread`

## Architecture

### Server Error Flow

```
Exception occurs
    ↓
Exception caught
    ↓
Error message printed to System.err
    ↓
Stack trace printed to System.err via printStackTrace()
    ↓
Server process stderr stream
    ↓
Client captures stderr
    ↓
Displayed in "Server Stderr" tab
```

### Stderr Capture Flow

```
Server Process
    ↓ stderr
ProcessBuilder (PIPE redirect)
    ↓
StdioClientTransport.stderrReader (BufferedReader)
    ↓
stderrReaderThread (background thread)
    ↓
Read line by line
    ↓
Notify all CommunicationListener instances
    ↓
McpClientController.onServerStderr()
    ↓
Platform.runLater() → UI thread
    ↓
serverStderrArea.appendText()
    ↓
Displayed to user
```

## Benefits

### For Debugging

✅ **Complete stack traces** - Full error context available  
✅ **Real-time visibility** - See errors as they happen  
✅ **No console needed** - All errors in GUI  
✅ **Persistent** - Errors stay visible in tab  
✅ **Auto-scroll** - Latest errors always visible  

### For Development

✅ **Better error diagnosis** - Stack traces show exact error location  
✅ **Separate concerns** - Stderr separate from communication log  
✅ **Thread-safe** - Background stderr reading doesn't block main thread  
✅ **Clean separation** - Each output type has its own tab  

## Example Usage

### Server Exception

When a server-side exception occurs:

**Server logs (stderr):**
```
Error handling request 'tools/call': Connection not found: invalid_db
java.lang.IllegalArgumentException: Connection not found: invalid_db
    at org.peacetalk.jmcp.jdbc.ConnectionManager.getContext(ConnectionManager.java:85)
    at org.peacetalk.jmcp.jdbc.JdbcToolsHandler.handleCallTool(JdbcToolsHandler.java:115)
    at org.peacetalk.jmcp.jdbc.JdbcToolsHandler.handle(JdbcToolsHandler.java:65)
    ...
```

**Client displays:**
- **Result tab:** Error message from MCP response
- **Communication Log tab:** JSON-RPC request/response
- **Server Stderr tab:** Full stack trace (above)

### Normal Operation

During normal operation:
- **Result tab:** Shows query results, table listings, etc.
- **Communication Log tab:** Shows JSON-RPC traffic
- **Server Stderr tab:** Shows server startup messages, warnings, any errors

## UI Layout

```
┌────────────────────────────────────────────────────────┐
│                    Output                               │
├─────────┬──────────────────┬──────────────────────────┤
│ Result  │ Communication Log│ Server Stderr  ← NEW     │
└─────────┴──────────────────┴──────────────────────────┘
│                                                         │
│  [Error output from server appears here]              │
│  JDBC MCP Server starting...                           │
│  Driver cache: /Users/user/.jmcp/drivers              │
│  Connections: 2                                        │
│  Registering connection: default                       │
│  Registering connection: analytics                     │
│                                                         │
│  Error handling request: Connection not found          │
│  java.lang.IllegalArgumentException...                │
│      at org.peacetalk...                              │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Files Modified

### Server-Side
1. ✅ `JdbcToolsHandler.java` - Added stack trace logging
2. ✅ `McpServer.java` - Added stack trace logging
3. ✅ `Main.java` - Added stack trace logging to shutdown hook

### Client-Side
4. ✅ `CommunicationListener.java` - Added `onServerStderr()` method
5. ✅ `StdioClientTransport.java` - Capture and read stderr stream
6. ✅ `McpClient.fxml` - Added "Server Stderr" tab
7. ✅ `McpClientController.java` - Handle and display stderr
8. ✅ `styles.css` - Already had monospace styling (applied to stderr tab)

## Thread Safety

The stderr reader is thread-safe:
- ✅ Background daemon thread reads stderr
- ✅ CopyOnWriteArrayList for listeners (thread-safe)
- ✅ Platform.runLater() for UI updates
- ✅ Proper cleanup on disconnect
- ✅ Exception handling in listener notifications

## Testing

### Manual Test Cases

1. **Normal Operation:**
   - Start server
   - Check "Server Stderr" tab shows startup messages
   
2. **Connection Error:**
   - Specify invalid database_id
   - Check "Server Stderr" tab shows stack trace
   
3. **SQL Error:**
   - Execute invalid SQL
   - Check "Server Stderr" tab shows error details
   
4. **Disconnect:**
   - Disconnect from server
   - Check "Server Stderr" tab is cleared
   
5. **Reconnect:**
   - Connect to server again
   - Check "Server Stderr" tab shows new startup messages

## Compilation Status

✅ **All modules compile successfully**  
✅ **No errors**  
✅ **Interface changes properly implemented**  

## Summary

The codebase now has comprehensive error logging:
- **Server:** All exceptions print stack traces to stderr
- **Client:** Captures and displays server stderr in real-time
- **UI:** New "Server Stderr" tab for easy debugging
- **Thread-safe:** Background stderr reading doesn't block
- **User-friendly:** Auto-scrolling, clear separation of outputs

This makes debugging server issues much easier and provides developers with full context when errors occur! 🎉

