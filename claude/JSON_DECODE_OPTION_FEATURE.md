# JSON Decoding Option in Output Display

**Date:** December 28, 2025

## Feature

Added an optional feature to automatically decode JSON strings in the `text` field of tool results and display them as formatted JSON objects in the output window.

## User Interface

**Added:** Checkbox labeled "Decode JSON in text fields" in the Output section header
- **Location:** Top of the output panel, next to the "Output" label
- **Default State:** Checked (enabled)
- **Behavior:** Only affects new results displayed after the option is changed

## How It Works

### When Enabled (Default)

When a tool returns a result like:
```json
{
  "content": [
    {
      "type": "text",
      "text": "{\"table\":\"users\",\"schema\":\"public\",\"columns\":[...]}"
    }
  ],
  "isError": false
}
```

The display will show:
```json
{
  "content": [
    {
      "type": "text",
      "text": {
        "table": "users",
        "schema": "public",
        "columns": [...]
      }
    }
  ],
  "isError": false
}
```

The JSON string is automatically parsed and pretty-printed for better readability.

### When Disabled

The result is displayed exactly as returned by the server, with the JSON string encoded in the `text` field.

## Implementation Details

### Transformation Flow

```
Tool Execution
    ↓
Result Received (CallToolResult)
    ↓
Check if "Decode JSON" is enabled
    ↓ (if enabled)
decodeJsonInResult()
    ↓
For each Content in result.content[]
    ↓
decodeJsonInContent()
    ↓
Check if type == "text" and text looks like JSON
    ↓
Try to parse JSON
    ↓ (if valid)
Pretty-print and return new Content with decoded text
    ↓
Display transformed result
```

### Key Methods

**`decodeJsonInResult(CallToolResult result)`**
- Transforms a CallToolResult by decoding JSON in all text content items
- Returns a DisplayResult wrapper object (not a real CallToolResult)
- Original result is unchanged

**`decodeJsonInContent(Content content)`**
- Checks if content is text type with JSON string
- Validates JSON starts with `{` or `[`
- Attempts to parse as JSON using Jackson ObjectMapper
- If valid: Returns DisplayContent wrapper with decoded object
- If invalid: Returns original content unchanged

**Display Wrapper Classes:**

```java
// Wrapper that allows text field to be an Object instead of String
private static class DisplayContent {
    public final String type;
    public final Object text;  // Can hold decoded JSON object
}

// Wrapper for the complete result
private static class DisplayResult {
    public final List<Object> content;  // Can hold mix of Content and DisplayContent
    public final Boolean isError;
}
```

These wrappers allow Jackson to serialize the `text` field as a JSON object rather than a string, making the decoded JSON appear inline in the display.

### Detection Logic

A text field is considered to contain JSON if:
1. Content type is `"text"`
2. Text field is not null
3. Trimmed text starts with `{` or `[`
4. Text successfully parses as valid JSON

### Error Handling

- **Parse failures:** Returns original content unchanged (graceful degradation)
- **Null content:** Returns original content unchanged
- **Non-text content:** Returns original content unchanged
- **Non-JSON text:** Returns original content unchanged

## Benefits

### Readability
✅ **Better formatting:** JSON objects are properly indented instead of escaped strings  
✅ **Easier to read:** Multi-level nested structures are clearly visible  
✅ **Color syntax:** JSON structure is easier to understand  

### Flexibility
✅ **Optional:** Can be toggled on/off per user preference  
✅ **Non-destructive:** Original data is never modified  
✅ **Display-only:** Transformation happens only at display time  

### Safety
✅ **Graceful fallback:** Invalid JSON displays as-is  
✅ **No data loss:** Original result is preserved  
✅ **Type-safe:** Only transforms text content  

## Important Notes

### MCP Specification Compliance

According to the MCP specification (https://spec.modelcontextprotocol.io/specification/):

```typescript
type TextContent = {
  type: "text";
  text: string;  // Must be a string
};
```

**The `text` field MUST be a string** - the MCP protocol does not support objects in the `text` field.

### Display-Only Transformation

This feature **only affects the display** in the client GUI. The actual MCP protocol messages remain compliant:
- Server sends: `"text": "{\"foo\":\"bar\"}"`  ✅ Valid per spec
- Client receives: Same string
- Client displays: Decoded JSON for readability
- Protocol unchanged: All messages remain MCP-compliant

### Option State

**Changing the checkbox does NOT affect already-displayed results.**

The option only applies to:
- New tool executions after the checkbox is toggled
- The transformation happens at display time, not storage time

To see the effect of toggling the option:
1. Toggle the checkbox
2. Execute a tool that returns JSON
3. Observe the difference in formatting

## Technical Implementation

### Code Location

**File:** `McpClientController.java`

**UI Component:**
```java
@FXML private CheckBox decodeJsonCheckBox;
```

**Transform Logic:**
```java
// In onExecute() method:
Object displayResult = decodeJsonCheckBox.isSelected() 
    ? decodeJsonInResult(result)  // Returns DisplayResult wrapper
    : result;                      // Returns actual CallToolResult

// Pretty print for display
String prettyResult = MAPPER.writerWithDefaultPrettyPrinter()
    .writeValueAsString(displayResult);
```

### Dependencies

- **Jackson ObjectMapper:** Used for JSON parsing and pretty-printing
- **Content model:** From `jmcp-core` module
- **CallToolResult model:** From `jmcp-core` module

### Performance

**Minimal Impact:**
- Transformation only runs when checkbox is enabled
- Only processes text content items
- Quick fail-fast for non-JSON text
- Jackson parsing is highly optimized

## Example Scenarios

### Scenario 1: Database Query Result

**Server Response:**
```json
{
  "content": [{
    "type": "text",
    "text": "{\"rows\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"count\":2}"
  }],
  "isError": false
}
```

**Displayed (when enabled):**
```json
{
  "content": [{
    "type": "text",
    "text": {
      "rows": [
        {"id": 1, "name": "Alice"},
        {"id": 2, "name": "Bob"}
      ],
      "count": 2
    }
  }],
  "isError": false
}
```

### Scenario 2: Plain Text (No Change)

**Server Response:**
```json
{
  "content": [{
    "type": "text",
    "text": "Query executed successfully"
  }],
  "isError": false
}
```

**Displayed (unchanged):**
```json
{
  "content": [{
    "type": "text",
    "text": "Query executed successfully"
  }],
  "isError": false
}
```

### Scenario 3: Image Content (No Change)

**Server Response:**
```json
{
  "content": [{
    "type": "image",
    "data": "base64encodeddata...",
    "mimeType": "image/png"
  }],
  "isError": false
}
```

**Displayed (unchanged):**
Image content is never transformed, only text content.

## Future Enhancements

Potential improvements (not implemented):
1. **Syntax highlighting** for decoded JSON
2. **Collapsible tree view** for JSON structures  
3. **Copy as JSON** button for decoded content
4. **Remember preference** across sessions (save to ClientPreferences)
5. **Batch transform** existing results when option changes

---

*"JSON is the lingua franca of web APIs."* - Douglas Crockford

In this case: Making that lingua franca actually readable in the client display!

