# Null Values Fix - JDBC Tool Results & Display Wrappers

**Date:** December 29, 2025

## Problem

Two issues with null values in MCP responses:

### Issue 1: Tool Results Include Explicit Nulls
JDBC tool result records were including explicit `null` values in JSON output:
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "content": [{
      "type": "text",
      "text": "{\"table\":\"sale\",\"schema\":null,\"rowCount\":6101}"
    }]
  }
}
```

Notice `"schema": null` instead of omitting the field.

### Issue 2: DisplayResult Shows Null isError
When decoding JSON in the client, the DisplayResult wrapper showed `isError: null`:
```json
{
  "content": [{
    "type": "text",
    "text": {
      "table": "sale",
      "schema": null,  // ← Also from issue 1
      "rowCount": 6101
    }
  }],
  "isError": null  // ← Should be omitted
}
```

## Root Cause

### Issue 1
The JDBC tool result records (in `jmcp-jdbc/tools/results/` package) were missing `@JsonInclude(JsonInclude.Include.NON_NULL)` annotation.

**Affected Files:**
- RowCountResult.java
- TableInfo.java
- SchemaInfo.java
- QueryResult.java
- TablesListResult.java
- ColumnMetadata.java
- ConnectionInfo.java
- IndexInfo.java
- ListConnectionsResult.java
- SchemasListResult.java
- TableDescription.java
- TablePreviewResult.java

### Issue 2
The client's display wrapper classes were also missing the annotation:
- DisplayResult.java
- DisplayContent.java

## Solution

Added `@JsonInclude(JsonInclude.Include.NON_NULL)` to all affected record classes.

### Changes Made

#### 1. JDBC Tool Results (12 files)

**Example - RowCountResult.java:**

**Before:**
```java
package org.peacetalk.jmcp.jdbc.tools.results;

public record RowCountResult(
    String table,
    String schema,
    long rowCount
) {
}
```

**After:**
```java
package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RowCountResult(
    String table,
    String schema,
    long rowCount
) {
}
```

Applied same pattern to all 12 result record classes.

#### 2. Client Display Wrappers (2 files)

**DisplayResult.java:**
```java
package org.peacetalk.jmcp.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisplayResult(List<Object> content, Boolean isError) {
}
```

**DisplayContent.java:**
```java
package org.peacetalk.jmcp.client;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisplayContent(String type, Object text) {
}
```

## Result

### Before Fix

**Tool Response (Issue 1):**
```json
{
  "table": "sale",
  "schema": null,      // ← Explicit null
  "rowCount": 6101
}
```

**Display Wrapper (Issue 2):**
```json
{
  "content": [{...}],
  "isError": null      // ← Explicit null
}
```

### After Fix

**Tool Response:**
```json
{
  "table": "sale",
  "rowCount": 6101     // ← schema field omitted
}
```

**Display Wrapper:**
```json
{
  "content": [{...}]   // ← isError field omitted
}
```

## Benefits

✅ **MCP Spec Compliant** - Follows TypeScript optional field semantics (`?`)  
✅ **JSON-RPC 2.0 Compliant** - Fields should be absent, not null  
✅ **Smaller Messages** - Reduced bandwidth (no unnecessary nulls)  
✅ **Clearer Intent** - Absence = not applicable  
✅ **Better Readability** - Less clutter in responses  

## Examples

### RowCount Tool

**Before:**
```json
{
  "content": [{
    "type": "text",
    "text": "{\"table\":\"orders\",\"schema\":null,\"rowCount\":1500}"
  }]
}
```

**After:**
```json
{
  "content": [{
    "type": "text",
    "text": "{\"table\":\"orders\",\"rowCount\":1500}"
  }]
}
```

### With JSON Decoding Enabled

**Before:**
```json
{
  "content": [{
    "type": "text",
    "text": {
      "table": "orders",
      "schema": null,
      "rowCount": 1500
    }
  }],
  "isError": null
}
```

**After:**
```json
{
  "content": [{
    "type": "text",
    "text": {
      "table": "orders",
      "rowCount": 1500
    }
  }]
}
```

**Improvements:**
- `schema: null` removed from decoded JSON
- `isError: null` removed from wrapper
- Much cleaner output!

## Files Modified

### JDBC Module (jmcp-jdbc)
1. `RowCountResult.java` - Added @JsonInclude
2. `TableInfo.java` - Added @JsonInclude
3. `SchemaInfo.java` - Added @JsonInclude
4. `QueryResult.java` - Added @JsonInclude
5. `TablesListResult.java` - Added @JsonInclude
6. `ColumnMetadata.java` - Added @JsonInclude
7. `ConnectionInfo.java` - Added @JsonInclude
8. `IndexInfo.java` - Added @JsonInclude
9. `ListConnectionsResult.java` - Added @JsonInclude
10. `SchemasListResult.java` - Added @JsonInclude
11. `TableDescription.java` - Added @JsonInclude
12. `TablePreviewResult.java` - Added @JsonInclude

### Client Module (jmcp-client)
13. `DisplayResult.java` - Added @JsonInclude
14. `DisplayContent.java` - Added @JsonInclude

## Compilation Status

✅ **Build Successful**
```
[INFO] BUILD SUCCESS
[INFO] Total time: 1.698 s
```

All modules compile successfully with the changes.

## Testing

### Manual Test

1. Connect to database
2. Execute `jdbc_get_row_count` tool with table that has no schema
3. Observe response **without** `"schema": null`
4. Enable JSON decoding in client
5. Execute same tool
6. Observe decoded output **without** `"isError": null`

### Expected Behavior

**Tool Results:**
- Null fields are omitted from JSON
- Only non-null fields are included

**Display Wrappers:**
- `isError` field only appears if value is `true`
- Decoded JSON fields omit nulls

## Consistency Check

All model classes in the project now properly use `@JsonInclude(JsonInclude.Include.NON_NULL)`:

✅ Core models (JsonRpcRequest, JsonRpcResponse, etc.)  
✅ MCP protocol models (Tool, CallToolResult, etc.)  
✅ JDBC tool results (all 12 result records)  
✅ Client display wrappers (DisplayResult, DisplayContent)  

The project now has **consistent null handling** throughout.

## Related Documentation

See `/claude/MCP_NULL_HANDLING_ANALYSIS.md` for detailed analysis of why eliding nulls is the correct approach per MCP and JSON-RPC 2.0 specifications.

---

*"Simplicity is the ultimate sophistication."* - Leonardo da Vinci

In JSON responses: Less is more - omit the nulls!

