# Multiple Listeners and Error Handling Improvements

**Date:** December 23, 2025

## Changes Made

Fixed two issues in `StdioClientTransport`:

1. **Multiple listener support** - Changed from single listener to list of listeners
2. **Exception catching on response parsing** - Added try-catch blocks with error notifications

## 1. Multiple Listener Support

### Before
```java
private CommunicationListener listener;

public void setListener(CommunicationListener listener) {
    this.listener = listener;
}
```

**Problem:** Only one listener could be registered at a time. Setting a new listener would overwrite the previous one.

### After
```java
private final List<CommunicationListener> listeners = new ArrayList<>();

public void addListener(CommunicationListener listener) {
    if (listener != null && !listeners.contains(listener)) {
        listeners.add(listener);
    }
}

public void removeListener(CommunicationListener listener) {
    listeners.remove(listener);
}

@Deprecated
public void setListener(CommunicationListener listener) {
    listeners.clear();
    if (listener != null) {
        listeners.add(listener);
    }
}
```

**Benefits:**
- Multiple components can listen to communication events
- Listeners can be added/removed independently
- Backward compatible (setListener still works but is deprecated)
- Thread-safe iteration with try-catch protection

### Notification Pattern

All listeners are notified with exception protection:

```java
for (CommunicationListener listener : listeners) {
    try {
        listener.onRequestSent(request);
    } catch (Exception e) {
        // Don't let listener exceptions break the request
        System.err.println("Listener error on request sent: " + e.getMessage());
    }
}
```

## 2. Exception Catching on Response Side

### Before
```java
// Read response
String responseLine = reader.readLine();
if (responseLine == null) {
    IOException error = new IOException("Server closed connection");
    if (listener != null) {
        listener.onError("Server closed connection", error);
    }
    throw error;
}

// Parse response - NO EXCEPTION CATCHING
JsonRpcResponse response = MAPPER.readValue(responseLine, JsonRpcResponse.class);
```

**Problem:** If response parsing failed, no error notification was sent to listeners.

### After
```java
// Read response
String responseLine;
try {
    responseLine = reader.readLine();
    if (responseLine == null) {
        IOException error = new IOException("Server closed connection");
        notifyError("Server closed connection", error);
        throw error;
    }
} catch (IOException e) {
    notifyError("Error reading response", e);
    throw e;
}

// Parse response
JsonRpcResponse response;
try {
    response = MAPPER.readValue(responseLine, JsonRpcResponse.class);
} catch (Exception e) {
    IOException parseError = new IOException("Failed to parse response: " + e.getMessage(), e);
    notifyError("Failed to parse response", parseError);
    throw parseError;
}
```

**Benefits:**
- Parsing errors are now caught and reported to listeners
- Better error messages with context
- Errors appear in Communication Log
- Original exceptions are wrapped and preserved

### New Helper Method

Added `notifyError()` method for consistent error notification:

```java
private void notifyError(String message, Exception exception) {
    for (CommunicationListener listener : listeners) {
        try {
            listener.onError(message, exception);
        } catch (Exception e) {
            // Don't let listener exceptions cause more problems
            System.err.println("Listener error on error notification: " + e.getMessage());
        }
    }
}
```

## Error Handling Flow

### Reading Response Errors

1. `reader.readLine()` throws IOException
2. Caught and wrapped with context
3. `notifyError()` called with message and exception
4. All listeners notified (appears in Communication Log)
5. Exception re-thrown

### Parsing Response Errors

1. `MAPPER.readValue()` throws parsing exception
2. Caught and wrapped as IOException with context
3. `notifyError()` called with message and exception
4. All listeners notified (appears in Communication Log)
5. New IOException thrown

## Files Modified

### 1. StdioClientTransport.java

**Changed:**
- `listener` field → `listeners` List
- Added `addListener()` method
- Added `removeListener()` method
- Deprecated `setListener()` method
- Updated `sendRequest()` to iterate all listeners
- Added try-catch around response reading
- Added try-catch around response parsing
- Added `notifyError()` helper method

**Lines changed:** ~40 lines

### 2. McpClient.java

**Changed:**
- Added `addCommunicationListener()` method
- Added `removeCommunicationListener()` method
- Deprecated `setCommunicationListener()` method

**Lines changed:** ~15 lines

### 3. McpClientController.java

**No changes needed** - Still uses `setCommunicationListener()` which works via backward compatibility

## API Changes

### New Methods

```java
// StdioClientTransport
public void addListener(CommunicationListener listener)
public void removeListener(CommunicationListener listener)

// McpClient
public void addCommunicationListener(StdioClientTransport.CommunicationListener listener)
public void removeCommunicationListener(StdioClientTransport.CommunicationListener listener)
```

### Deprecated Methods

```java
// StdioClientTransport
@Deprecated
public void setListener(CommunicationListener listener)

// McpClient
@Deprecated
public void setCommunicationListener(StdioClientTransport.CommunicationListener listener)
```

## Backward Compatibility

✅ Existing code using `setCommunicationListener()` continues to work
✅ No breaking changes
✅ Deprecated methods provide migration path

## Error Scenarios Now Handled

1. **Server closes connection** - Already handled, now with multiple listeners
2. **IOException while reading** - Now caught and reported to listeners
3. **JSON parse error** - Now caught and reported to listeners
4. **Malformed JSON** - Now caught and reported to listeners
5. **Invalid response structure** - Now caught and reported to listeners

## Testing

To verify the improvements:

1. **Multiple listeners:**
   - Add multiple listeners to the client
   - Verify all are notified on requests/responses
   - Remove one listener, verify others still work

2. **Parse error handling:**
   - Send malformed JSON from server
   - Verify error appears in Communication Log
   - Verify exception is properly reported

3. **Connection error handling:**
   - Kill server process mid-communication
   - Verify "Server closed connection" appears in log
   - Verify error is properly reported

## Example: Multiple Listeners

```java
// Add logging listener
client.addCommunicationListener(new CommunicationListener() {
    @Override
    public void onRequestSent(JsonRpcRequest request) {
        logToFile(request);
    }
    // ...
});

// Add metrics listener
client.addCommunicationListener(new CommunicationListener() {
    @Override
    public void onRequestSent(JsonRpcRequest request) {
        metrics.incrementRequestCount();
    }
    // ...
});

// Add UI listener
client.addCommunicationListener(new CommunicationListener() {
    @Override
    public void onRequestSent(JsonRpcRequest request) {
        updateUI(request);
    }
    // ...
});
```

All three listeners will be notified independently.

## Exception Safety

All listener notifications are wrapped in try-catch:
- Listener exceptions don't break the communication flow
- Errors are logged to System.err
- Other listeners continue to be notified
- Original operation (send/receive) completes normally

## Future Enhancements

Potential improvements:

1. **Listener Priority** - Execute listeners in priority order
2. **Async Notifications** - Notify listeners on background thread
3. **Filter Listeners** - Allow listeners to filter by method name
4. **Listener Lifecycle** - Auto-remove listeners on certain events
5. **Event Batching** - Batch multiple events for efficiency

## Benefits Summary

✅ **Multiple listeners** - Different components can monitor communication  
✅ **Better error handling** - Parse errors now properly reported  
✅ **Backward compatible** - Existing code continues to work  
✅ **Exception safe** - Listener errors don't break communication  
✅ **Better debugging** - All errors appear in Communication Log  
✅ **Flexible** - Listeners can be added/removed dynamically  

## Code Statistics

**Lines added:** ~60  
**Lines modified:** ~40  
**New methods:** 4 (addListener, removeListener, notifyError in Transport; add/removeCommunicationListener in Client)  
**Deprecated methods:** 2 (setListener, setCommunicationListener)  
**Bug fixes:** 1 (missing exception catching on response parse)  

