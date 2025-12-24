# Preferences Refactoring - ClientPreferences Class

**Date:** December 24, 2025

## Summary

Refactored preferences management into a dedicated `ClientPreferences` class to eliminate null checks and improve code maintainability.

## Changes Made

### 1. Created ClientPreferences.java (NEW)

**Location:** `/jmcp-client/src/main/java/org/peacetalk/jmcp/client/ClientPreferences.java`

**Purpose:** Encapsulates all preferences logic in a single, reusable class.

**Features:**
```java
public class ClientPreferences {
    // No public constructor parameters needed
    public ClientPreferences() { ... }
    
    // Get server command (returns empty string if not set)
    public String getServerCommand() { ... }
    
    // Save server command (validates input)
    public void setServerCommand(String command) { ... }
    
    // Clear all preferences
    public void clear() { ... }
}
```

**Benefits:**
- ✅ No null checks needed (instance always valid)
- ✅ Returns sensible defaults (empty string, not null)
- ✅ Validates input (checks for null/blank)
- ✅ Single responsibility (only handles preferences)
- ✅ Easy to test
- ✅ Easy to extend (add more preferences later)

### 2. Updated McpClientController.java

**Removed:**
```java
import java.util.prefs.Preferences;
private static final String PREF_SERVER_COMMAND = "server.command";
private Preferences preferences;
```

**Added:**
```java
private final ClientPreferences preferences = new ClientPreferences();
```

**Simplified setupPreferences():**

**Before:**
```java
public void setupPreferences() {
    preferences = Preferences.userNodeForPackage(McpClientController.class);
    String savedCommand = preferences.get(PREF_SERVER_COMMAND, "");
    serverCommandField.setText(savedCommand);
}
```

**After:**
```java
public void setupPreferences() {
    serverCommandField.setText(preferences.getServerCommand());
}
```

**Simplified saving preferences:**

**Before:**
```java
if (preferences != null) {
    preferences.put(PREF_SERVER_COMMAND, command);
}
```

**After:**
```java
preferences.setServerCommand(command);
```

### 3. Fixed CommunicationListener Reference

**Issue:** Previous change incorrectly referenced `StdioClientTransport.CommunicationListener` when it had already been refactored to a standalone interface.

**Fixed:**
```java
// WRONG (old inner class reference)
client.addCommunicationListener(new StdioClientTransport.CommunicationListener() { ... });

// CORRECT (standalone interface)
client.addCommunicationListener(new CommunicationListener() { ... });
```

**Added imports:**
```java
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.JsonRpcResponse;
```

## Benefits of Refactoring

### Before (Scattered Logic)

```java
// Controller had to know about Preferences API
private Preferences preferences;

// Null checks everywhere
if (preferences != null) {
    preferences.put(PREF_SERVER_COMMAND, command);
}

// Magic strings scattered
preferences.get(PREF_SERVER_COMMAND, "");

// Multiple responsibilities
```

### After (Encapsulated Logic)

```java
// Simple, always-valid instance
private final ClientPreferences preferences = new ClientPreferences();

// No null checks needed
preferences.setServerCommand(command);

// Clean API
String cmd = preferences.getServerCommand();

// Single responsibility
```

## Code Quality Improvements

### 1. Eliminated Null Checks

**Before:** Had to check `if (preferences != null)` before every use  
**After:** `preferences` is always valid (final field, initialized immediately)

### 2. Encapsulation

**Before:** Controller knew about:
- Preferences API
- userNodeForPackage
- Preference keys ("server.command")
- Default values

**After:** Controller only knows:
- `preferences.getServerCommand()`
- `preferences.setServerCommand(command)`

### 3. Testability

**ClientPreferences can be easily tested:**
```java
@Test
void testServerCommandPersistence() {
    ClientPreferences prefs = new ClientPreferences();
    prefs.setServerCommand("./test.sh");
    assertEquals("./test.sh", prefs.getServerCommand());
}
```

**Controller can be tested with mocked ClientPreferences:**
```java
@Test
void testControllerUsesPreferences() {
    ClientPreferences mockPrefs = mock(ClientPreferences.class);
    when(mockPrefs.getServerCommand()).thenReturn("./run.sh");
    // Test controller behavior
}
```

### 4. Extensibility

Easy to add more preferences:
```java
public class ClientPreferences {
    private static final String PREF_SERVER_COMMAND = "server.command";
    private static final String PREF_AUTO_CONNECT = "auto.connect";
    private static final String PREF_LAST_TOOL = "last.tool";
    
    public String getServerCommand() { ... }
    public void setServerCommand(String command) { ... }
    
    public boolean isAutoConnect() { ... }
    public void setAutoConnect(boolean autoConnect) { ... }
    
    public String getLastTool() { ... }
    public void setLastTool(String toolName) { ... }
}
```

## Implementation Details

### Storage Location

Preferences are stored using Java Preferences API under:
```
/org/peacetalk/jmcp/client
```

Platform-specific locations:
- **Linux:** `~/.java/.userPrefs/org/peacetalk/jmcp/client/prefs.xml`
- **macOS:** `~/Library/Preferences/com.apple.java.util.prefs.plist`
- **Windows:** Registry: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\peacetalk\jmcp\client`

### Validation

The `setServerCommand()` method validates input:
```java
public void setServerCommand(String command) {
    if (command != null && !command.isBlank()) {
        preferences.put(PREF_SERVER_COMMAND, command);
    }
}
```

This prevents:
- Null values from being stored
- Blank strings from being stored
- Invalid state in preferences

### Default Values

The `getServerCommand()` method returns safe defaults:
```java
public String getServerCommand() {
    return preferences.get(PREF_SERVER_COMMAND, "");
}
```

Always returns a non-null string (empty string if not set).

## Files Modified

1. **ClientPreferences.java** (NEW) - 51 lines
   - Encapsulates all preferences logic
   - Clean API, no null checks needed

2. **McpClientController.java** (MODIFIED)
   - Removed: Preferences import, field, constant
   - Added: ClientPreferences field (final)
   - Simplified: setupPreferences(), onConnect()
   - Fixed: CommunicationListener reference
   - Added: JsonRpcRequest, JsonRpcResponse imports

## Testing

### Manual Testing

1. **First run:**
   ```bash
   ./run-client.sh
   # Expected: Server command field is blank
   # Enter "./run.sh" and connect
   # Expected: Connection succeeds
   ```

2. **Second run:**
   ```bash
   ./run-client.sh
   # Expected: Server command field shows "./run.sh"
   ```

3. **Change command:**
   ```bash
   # Change to different command and connect
   # Expected: New command is saved
   # Next run: Shows new command
   ```

### Unit Testing (Future)

```java
@Test
void testPreferencesDefaultValue() {
    ClientPreferences prefs = new ClientPreferences();
    assertEquals("", prefs.getServerCommand());
}

@Test
void testPreferencesSetAndGet() {
    ClientPreferences prefs = new ClientPreferences();
    prefs.setServerCommand("./test.sh");
    assertEquals("./test.sh", prefs.getServerCommand());
}

@Test
void testPreferencesIgnoresNull() {
    ClientPreferences prefs = new ClientPreferences();
    prefs.setServerCommand("./valid.sh");
    prefs.setServerCommand(null); // Should not clear
    assertEquals("./valid.sh", prefs.getServerCommand());
}

@Test
void testPreferencesIgnoresBlank() {
    ClientPreferences prefs = new ClientPreferences();
    prefs.setServerCommand("./valid.sh");
    prefs.setServerCommand("   "); // Should not clear
    assertEquals("./valid.sh", prefs.getServerCommand());
}
```

## Design Patterns Used

### 1. Facade Pattern
`ClientPreferences` provides a simple facade over the complex Preferences API.

### 2. Encapsulation
All preferences logic is hidden inside `ClientPreferences`.

### 3. Defensive Programming
Validates all inputs, returns safe defaults, never returns null.

## Future Enhancements

Potential additions to `ClientPreferences`:

1. **Auto-connect preference:**
   ```java
   public boolean isAutoConnect() { ... }
   public void setAutoConnect(boolean autoConnect) { ... }
   ```

2. **Recent commands list:**
   ```java
   public List<String> getRecentCommands() { ... }
   public void addRecentCommand(String command) { ... }
   ```

3. **Connection profiles:**
   ```java
   public void saveProfile(String name, ConnectionProfile profile) { ... }
   public ConnectionProfile loadProfile(String name) { ... }
   ```

4. **UI preferences:**
   ```java
   public double getFontScale() { ... }
   public void setFontScale(double scale) { ... }
   ```

## Conclusion

✅ **Refactoring Complete**

Benefits achieved:
- Clean, maintainable code
- No null checks needed
- Better encapsulation
- Easier to test
- Easier to extend
- Fixed CommunicationListener reference

**Status:** Ready to use immediately

