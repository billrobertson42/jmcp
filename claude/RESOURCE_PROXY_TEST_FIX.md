# ResourceProxyToolTest Compilation Fix - February 16, 2026

## Problem
The `ResourceProxyToolTest` was failing to compile because it was using classes that don't exist:
- `Content` (for tool responses)
- `TextContent` (non-existent)

These were being used instead of the correct resource classes.

## Root Cause
The test was written with tool response classes (`Content`) instead of resource response classes (`ResourceContents`). The wildcard import `org.peacetalk.jmcp.core.model.*` was importing both, causing confusion.

## Solution

### 1. Fixed Incorrect Class Usage
**Changed from:**
- `Content content` → `ResourceContents content`
- `TextContent textContent` → Direct use of `ResourceContents`
- `content.uri()` → Already correct for `ResourceContents`
- `textContent.text()` → `content.text()`

### 2. Replaced Wildcard Import with Specific Imports
**Before:**
```java
import org.peacetalk.jmcp.core.model.*;
```

**After:**
```java
import org.peacetalk.jmcp.core.model.JsonRpcRequest;
import org.peacetalk.jmcp.core.model.ListResourcesResult;
import org.peacetalk.jmcp.core.model.ReadResourceResult;
import org.peacetalk.jmcp.core.model.ResourceContents;
import org.peacetalk.jmcp.core.model.ResourceDescriptor;
```

### 3. Removed Unused Import
```java
import static org.mockito.ArgumentMatchers.any; // Removed
```

## Changes Made

### In `testReadResourceSuccess()`
```java
// Before:
Content content = readResult.contents().get(0);
TextContent textContent = (TextContent) content;
assertEquals("{\"data\": \"test\"}", textContent.text());

// After:
ResourceContents content = readResult.contents().get(0);
assertEquals("{\"data\": \"test\"}", content.text());
```

### In `testReadResourceWithComplexContent()`
```java
// Before:
Content content = readResult.contents().get(0);
TextContent textContent = (TextContent) content;
assertEquals(complexJson, textContent.text());

// After:
ResourceContents content = readResult.contents().get(0);
assertEquals(complexJson, content.text());
```

## Verification

### Compilation Status
✅ No compilation errors
⚠️ Only warnings remain:
- "Can be replaced with 'getFirst()' call" (stylistic, not blocking)
- "Exception 'java.lang.Exception' is never thrown" (overly broad throws declaration)

### Correct Classes Used

| Purpose | Correct Class | Incorrect Class (Fixed) |
|---------|---------------|-------------------------|
| Resource response wrapper | `ReadResourceResult` | ✓ (was correct) |
| Resource content item | `ResourceContents` | `Content`, `TextContent` |
| Resource list wrapper | `ListResourcesResult` | ✓ (was correct) |
| Resource descriptor | `ResourceDescriptor` | ✓ (was correct) |

## Key Differences Between Tool and Resource Content

### Tool Response Content (`Content`)
```java
// For tool call responses
public record Content(
    String type,      // "text" or "image"
    String text,
    String data,
    String mimeType
)
```

### Resource Content (`ResourceContents`)
```java
// For resource read responses
public record ResourceContents(
    String uri,       // ← Resource URI
    String mimeType,
    String text,      // ← Text content
    String blob       // ← Or binary blob
)
```

**Key difference:** `ResourceContents` includes the URI of the resource being read, while `Content` is just the payload data from a tool execution.

## Files Modified
1. `/Users/bill/dev/mcp/jmcp/jmcp-server/src/test/java/test/org/peacetalk/jmcp/server/tools/ResourceProxyToolTest.java`
   - Fixed imports
   - Fixed class references in 2 test methods
   - Removed unused imports

## Test Status
All compilation errors resolved. Tests should now compile and run successfully.

