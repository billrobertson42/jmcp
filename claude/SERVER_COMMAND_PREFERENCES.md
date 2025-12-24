# Server Command Preferences Implementation

**Date:** December 24, 2025

## Feature Summary

The MCP Client now saves and restores the server command using Java Preferences API.

## Changes Made

### 1. Updated McpClientController.java

**Added:**
- Import: `java.util.prefs.Preferences`
- Constant: `PREF_SERVER_COMMAND = "server.command"`
- Field: `private Preferences preferences;`

**New Method:**
```java
public void setupPreferences() {
    preferences = Preferences.userNodeForPackage(McpClientController.class);
    
    // Load saved server command, default to empty string if not set
    String savedCommand = preferences.get(PREF_SERVER_COMMAND, "");
    serverCommandField.setText(savedCommand);
}
```

**Modified initialize():**
- Removed hardcoded `serverCommandField.setText("./run.sh")`
- Now loads from preferences via `setupPreferences()`

**Modified onConnect():**
- After successful connection, saves the command:
```java
// Save the server command to preferences after successful connection
if (preferences != null) {
    preferences.put(PREF_SERVER_COMMAND, command);
}
```

### 2. Updated McpClientApp.java

**Added:**
- Get controller after loading FXML
- Call `controller.setupPreferences()` before showing window

```java
McpClientController controller = loader.getController();
if (controller != null) {
    controller.setupPreferences();
}
```

### 3. Updated module-info.java

**Added module requirement:**
```java
requires java.prefs;
```

## How It Works

### Startup Sequence

1. **Application starts** → `McpClientApp.main()`
2. **FXML loaded** → Controller created
3. **Controller.initialize()** → UI components initialized
4. **Controller.setupPreferences()** → Loads saved server command
5. **Window shown** → User sees last-used command (or blank)

### On Connect

1. User enters/edits server command
2. Clicks Connect button
3. Connection attempt starts
4. **If successful** → Command saved to preferences
5. **If failed** → Command not saved (user might want to correct it)

### Storage Location

Java Preferences API stores data in platform-specific locations:

| Platform | Location |
|----------|----------|
| **Linux** | `~/.java/.userPrefs/org/peacetalk/jmcp/client/prefs.xml` |
| **macOS** | `~/Library/Preferences/com.apple.java.util.prefs.plist` |
| **Windows** | Registry: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\peacetalk\jmcp\client` |

### Preference Key

The command is stored with key: `"server.command"`

Full path: `org.peacetalk.jmcp.client` → `server.command`

## User Experience

### First Run
1. Start application
2. Server command field is **blank** (no saved preference)
3. User enters command (e.g., `./run.sh`)
4. Clicks Connect
5. Connection succeeds → Command is saved

### Subsequent Runs
1. Start application
2. Server command field shows `./run.sh` (loaded from preferences)
3. User can connect immediately or edit the command
4. If edited and connection succeeds → New command is saved

### Command Change
1. User changes command from `./run.sh` to `/path/to/other-server.sh`
2. Clicks Connect
3. If successful → New command saved
4. Next time app starts → Shows new command

### Failed Connection
1. User enters wrong command
2. Clicks Connect
3. Connection fails
4. Command is **not saved** (only successful connections save)
5. User can correct the command

## Benefits

✅ **Convenience** - Don't re-type command every time  
✅ **User-specific** - Each user has their own preference  
✅ **Cross-platform** - Works on Linux, macOS, Windows  
✅ **Standard API** - Uses Java built-in Preferences  
✅ **Persistent** - Survives application restarts  
✅ **Smart saving** - Only saves on successful connection  

## Testing

### Test 1: First Run (No Saved Preference)
```bash
# Clear any existing preference (optional)
rm -rf ~/.java/.userPrefs/org/peacetalk/jmcp/client/

# Run client
./run-client.sh

# Expected: Server command field is blank
# Action: Enter "./run.sh" and connect
# Expected: Command is saved
```

### Test 2: Second Run (Preference Exists)
```bash
# Run client again
./run-client.sh

# Expected: Server command field shows "./run.sh"
# Action: Can connect immediately
```

### Test 3: Change Command
```bash
# Run client
./run-client.sh

# Expected: Shows saved command
# Action: Change to different command, connect
# Expected: New command is saved
# Next run: Shows new command
```

### Test 4: Failed Connection
```bash
# Run client
./run-client.sh

# Action: Enter invalid command, click Connect
# Expected: Connection fails, command NOT saved
# Next run: Shows previous valid command (or blank)
```

## Implementation Details

### Why Save on Success Only?

If we saved on every connect attempt:
- User types wrong command → Saved
- Next run → Shows wrong command
- User has to correct it again

By saving only on success:
- Wrong commands are not persisted
- Next run shows last **working** command
- Better user experience

### Why userNodeForPackage?

```java
Preferences.userNodeForPackage(McpClientController.class)
```

This creates a preference node at:
```
/org/peacetalk/jmcp/client
```

Benefits:
- Scoped to this application
- Won't conflict with other apps
- Easy to locate and manage

### Thread Safety

Preferences API is thread-safe:
- Can read/write from any thread
- We write from JavaFX Application Thread
- No synchronization needed

## Future Enhancements

Potential additions:

1. **Recent Commands List**
   - Save last 5 commands
   - Show dropdown with history
   - Quick selection

2. **Multiple Profiles**
   - Save named connection profiles
   - Dev, Staging, Production
   - Quick switching

3. **Import/Export**
   - Export preferences to file
   - Import on different machine
   - Team sharing

4. **Connection Validation**
   - Test command without connecting
   - Verify server executable exists
   - Show warnings

5. **Auto-connect Option**
   - Checkbox: "Auto-connect on startup"
   - If enabled, connect automatically
   - Uses saved command

## Code Statistics

**Files Modified:** 3
- McpClientController.java - Added preferences handling
- McpClientApp.java - Calls setupPreferences
- module-info.java - Added java.prefs requirement

**Lines Added:** ~20
**New Methods:** 1 (setupPreferences)
**New Preferences:** 1 (server.command)

## API Reference

### Java Preferences API

```java
// Get user preferences for a package
Preferences prefs = Preferences.userNodeForPackage(MyClass.class);

// Store a preference
prefs.put("key", "value");

// Retrieve a preference with default
String value = prefs.get("key", "defaultValue");

// Remove a preference
prefs.remove("key");

// Clear all preferences for this node
prefs.clear();
```

### Storage Hierarchy

```
User Root
└── org
    └── peacetalk
        └── jmcp
            └── client
                └── server.command = "./run.sh"
```

## Troubleshooting

### Preference Not Saving

**Check:**
1. Connection succeeds (only successful connections save)
2. No exception thrown during `preferences.put()`
3. Preferences object is not null

**Debug:**
```java
System.out.println("Saving command: " + command);
preferences.put(PREF_SERVER_COMMAND, command);
System.out.println("Saved to: " + preferences.absolutePath());
```

### Preference Not Loading

**Check:**
1. `setupPreferences()` is called
2. Called after `initialize()` completes
3. Preference exists in storage

**Debug:**
```java
String saved = preferences.get(PREF_SERVER_COMMAND, null);
System.out.println("Loaded command: " + saved);
```

### Clear All Preferences

```bash
# Linux/macOS
rm -rf ~/.java/.userPrefs/org/peacetalk/jmcp/client/

# Or programmatically:
Preferences prefs = Preferences.userNodeForPackage(McpClientController.class);
prefs.clear();
```

## Conclusion

✅ **Feature Complete**

The server command is now:
- Loaded from preferences at startup (blank if not set)
- Saved to preferences on successful connection
- Persisted across application restarts
- User-specific and platform-independent

**Status:** Ready to use immediately

