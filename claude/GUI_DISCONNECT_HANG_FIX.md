# GUI Disconnect Button Hang Fix

**Date:** December 28, 2025

## Problem

The disconnect button in the MCP client GUI was causing the application to hang/freeze when clicked.

## Root Cause

The disconnect operation was being executed **synchronously on the JavaFX Application Thread**, causing the GUI to freeze during the blocking I/O operations:

### The Blocking Chain

1. **User clicks Disconnect** → JavaFX event handler `onDisconnect()` called on Application Thread
2. **`mcpService.disconnect()`** → Calls `client.close()`
3. **`StdioClientTransport.close()`** → Contains multiple blocking operations:
   - `serverProcess.waitFor()` - **BLOCKS** waiting for process to terminate
   - `stderrReaderThread.join()` - **BLOCKS** waiting for thread to finish
4. **GUI freezes** - Application Thread is blocked, cannot process UI events

### Additional Issue in close()

The original `close()` implementation had an incorrect order of operations:
1. It closed streams
2. Then destroyed the process and **blocked waiting** with `waitFor()`
3. Only **after** waiting did it interrupt the stderr reader thread

This meant the stderr reader thread was still potentially blocking on `readLine()`, and we were waiting for the process to exit while a thread was still trying to read from it - a deadlock scenario.

## Solution

### 1. Move Disconnect to Background Thread

Changed `McpClientController.onDisconnect()` to run the disconnect operation in a daemon background thread:

```java
@FXML
private void onDisconnect() {
    // Disable button immediately
    disconnectButton.setDisable(true);
    statusLabel.setText("Disconnecting...");

    // Run disconnect in background thread - does not block GUI
    Thread disconnectThread = new Thread(() -> {
        try {
            mcpService.disconnect();
        } catch (Exception e) {
            System.err.println("Error during disconnect: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        // Update UI on JavaFX thread when done
        Platform.runLater(() -> {
            // Clear UI elements...
            statusLabel.setText("Disconnected");
            updateConnectionState(false);
        });
    });
    disconnectThread.setDaemon(true);  // Won't prevent app exit
    disconnectThread.start();
}
```

**Benefits:**
- ✅ GUI remains responsive during disconnect
- ✅ User can see "Disconnecting..." status
- ✅ No freezing even if disconnect takes several seconds
- ✅ Daemon thread won't prevent application exit

### 2. Fixed close() Order of Operations

Rewrote `StdioClientTransport.close()` with correct cleanup sequence:

```java
@Override
public void close() {
    // 1. Interrupt stderr reader thread FIRST
    if (stderrReaderThread != null && stderrReaderThread.isAlive()) {
        stderrReaderThread.interrupt();
    }

    // 2. Close streams to unblock any waiting I/O
    if (writer != null) { writer.close(); }
    if (reader != null) { reader.close(); }
    if (stderrReader != null) { stderrReader.close(); }

    // 3. Destroy process and wait with timeout
    if (serverProcess != null && serverProcess.isAlive()) {
        serverProcess.destroy();
        if (!serverProcess.waitFor(2, TimeUnit.SECONDS)) {
            serverProcess.destroyForcibly();  // Force kill
            serverProcess.waitFor(1, TimeUnit.SECONDS);
        }
    }

    // 4. Give stderr thread a moment to finish
    if (stderrReaderThread != null && stderrReaderThread.isAlive()) {
        stderrReaderThread.join(500);
    }
}
```

**Key Improvements:**
1. **Interrupt thread first** - Signals it to stop before we wait
2. **Close streams** - Unblocks any `readLine()` calls
3. **Timeout on waitFor** - Won't wait forever (max 2 seconds)
4. **Force kill if needed** - `destroyForcibly()` if graceful shutdown fails
5. **Bounded thread join** - Only wait 500ms for stderr thread

### Why the Original Hung

**Scenario:** Server process is running, stderr reader is blocked on `readLine()`

**Original Code:**
```
1. Close streams (stderr reader still blocked)
2. Destroy process
3. waitFor() - HANGS because:
   - stderr reader thread still trying to read
   - Process might not exit cleanly
   - No timeout, waits forever
4. (Never reaches) interrupt stderr thread
```

**Fixed Code:**
```
1. Interrupt stderr thread (signals it to stop)
2. Close streams (unblocks readLine())
3. Destroy process  
4. waitFor(2 seconds) - Bounded wait
5. destroyForcibly() if needed
6. join(500ms) - Bounded wait for thread cleanup
```

## Testing the Fix

### Before Fix
- Click Disconnect
- GUI freezes
- Application becomes unresponsive
- May require force-quit

### After Fix
- Click Disconnect
- Button disables immediately
- Status shows "Disconnecting..."
- GUI remains responsive
- UI updates to "Disconnected" state within 1-3 seconds
- No hanging or freezing

## Thread Safety Notes

- **CopyOnWriteArrayList** for listeners - Thread-safe iteration during concurrent modifications
- **Daemon threads** - Won't prevent JVM exit if app closes during disconnect
- **Platform.runLater()** - All UI updates happen on JavaFX Application Thread
- **Interrupt handling** - Properly propagates `InterruptedException` via `Thread.currentThread().interrupt()`

## Related Changes

This fix follows the same pattern used in other parts of the client:
- `onConnect()` - Already ran connection in background thread
- `onExecute()` - Already ran tool execution in background thread
- `onDisconnect()` - **NOW** also runs in background thread for consistency

## Impact

- ✅ **No functional changes** - Same behavior, just non-blocking
- ✅ **Better UX** - Responsive UI during disconnect
- ✅ **Safer** - Timeouts prevent infinite hangs
- ✅ **Consistent** - All long-running operations now in background threads

---

*"The most important property of a program is whether it accomplishes the intention of its user."* - C.A.R. Hoare

