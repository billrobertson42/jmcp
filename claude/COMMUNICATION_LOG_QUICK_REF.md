# Communication Log - Quick Reference

**Date:** December 23, 2025

## What It Does

Shows all JSON-RPC messages between the MCP client and server in a readable format.

## Location

Right panel → "Communication Log" tab (next to "Result" tab)

## Message Format

### Sent Request
```
================================================================================
>>> SENT tools/call <<<
================================================================================
{
  "jsonrpc" : "2.0",
  "id" : 3,
  "method" : "tools/call",
  "params" : { ... }
}
```

### Received Response
```
================================================================================
>>> RECEIVED Response <<<
================================================================================
{
  "jsonrpc" : "2.0",
  "id" : 3,
  "result" : { ... }
}
```

### Error
```
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
>>> ERROR <<<
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
Connection failed
Exception: IOException
Message: Server closed connection
```

## Features

✅ **Monospace font** - Fixed-width for JSON readability  
✅ **Pretty-printed** - Indented, formatted JSON  
✅ **Visual separators** - `===` for messages, `!!!` for errors  
✅ **Direction labels** - SENT vs RECEIVED  
✅ **Method names** - Shows what RPC method was called  
✅ **Error insertion** - Errors appear at the right point in the timeline  
✅ **Auto-scroll** - Latest messages always visible  
✅ **Read-only** - Can't accidentally edit  

## Typical Log Sequence

**On Connect:**
```
SENT initialize
RECEIVED Response (server info)
SENT notifications/initialized
SENT tools/list
RECEIVED Response (list of tools)
```

**On Tool Execution:**
```
SENT tools/call
RECEIVED Response (tool result)
```

## When to Use

- **Debugging** - See exactly what's being sent/received
- **Learning** - Understand the MCP protocol
- **Troubleshooting** - Find where errors occur
- **Verification** - Confirm parameters are correct

## How to Use

1. Connect to server
2. Click "Communication Log" tab
3. Watch messages appear as you interact
4. Disconnect to clear log

## Files Changed

- ✅ `McpClient.fxml` - Added TabPane with Result and Communication Log tabs
- ✅ `StdioClientTransport.java` - Added CommunicationListener interface
- ✅ `McpClient.java` - Added setCommunicationListener() method
- ✅ `McpClientController.java` - Added logging methods and listener setup

## Implementation Details

**Thread-safe:** Uses `Platform.runLater()` for UI updates  
**Format:** Jackson pretty printer with 2-space indentation  
**Cleared on:** Disconnect  
**Font:** Monospace (Consolas, Courier New)  
**Wrapping:** Disabled to preserve JSON formatting  

## Quick Tips

- Switch between tabs to see results vs. communication
- Log shows everything - both successful and failed operations
- Errors appear inline at the point they occurred
- The Result tab still works the same - log is additional info

## Example Use Case

**Problem:** Tool call returns error  
**Solution:** 
1. Check Communication Log tab
2. Find the SENT tools/call message
3. Verify parameters are correct
4. Look at RECEIVED Response
5. See error details

## What Gets Logged

✅ Initialize handshake  
✅ Tool list requests  
✅ Tool call requests  
✅ All responses  
✅ Connection errors  
✅ Protocol errors  
✅ Server crashes  

## What Doesn't Get Logged

❌ UI interactions (clicking buttons, selecting tools)  
❌ Local validation errors (before sending)  
❌ Internal client operations  

Only actual network communication is logged.

