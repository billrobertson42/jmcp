# Tool List Alphabetical Sorting

**Date:** December 23, 2025

## Change

Added alphabetical sorting to the tools list. When connecting to a server, tools are now displayed in alphabetical order (case-insensitive) instead of the order returned by the server.

## Implementation

**File:** `McpClientController.java`

**In the `onConnect()` method:**

```java
Platform.runLater(() -> {
    // Sort tools alphabetically by name before displaying
    var sortedTools = toolsResult.tools().stream()
            .sorted((t1, t2) -> t1.name().compareToIgnoreCase(t2.name()))
            .toList();
    
    toolsList.setItems(FXCollections.observableArrayList(sortedTools));
    // ...rest of connection handling
});
```

## Before

Tools were displayed in the order returned by the server:
```
describe-table
query
list-tables
preview-table
get-row-count
list-schemas
```

## After

Tools are displayed in alphabetical order:
```
describe-table
get-row-count
list-schemas
list-tables
preview-table
query
```

## Technical Details

### Sorting Approach

- **Stream API** - Uses Java Stream for functional sorting
- **Case-insensitive** - Uses `compareToIgnoreCase()` for natural alphabetical ordering
- **Immutable result** - `.toList()` creates an immutable list
- **Observable wrapper** - Wrapped in `FXCollections.observableArrayList()` for ListView binding

### Why Case-Insensitive?

Using `compareToIgnoreCase()` instead of `compareTo()` ensures:
- Natural alphabetical order (a, A, b, B instead of A, B, a, b)
- Tools starting with uppercase and lowercase are mixed properly
- More user-friendly display

### Performance

Sorting happens once when connecting, not on every UI update:
- Minimal performance impact (typically < 100 tools)
- Sorting time: O(n log n) where n = number of tools
- Done in background thread, doesn't block UI

## User Experience

### Benefits

1. **Predictability** - Tools always appear in the same order
2. **Searchability** - Easier to scan and find specific tools
3. **Consistency** - Same order across connections
4. **Professionalism** - Sorted lists look more polished

### Example

For a JDBC server with typical tools:
```
✓ describe-table     (was 3rd, now 1st)
✓ get-row-count      (was 5th, now 2nd)
✓ list-schemas       (was 6th, now 3rd)
✓ list-tables        (was 2nd, now 4th)
✓ preview-table      (was 4th, now 5th)
✓ query              (was 1st, now 6th)
```

## Alternative Approaches Considered

### 1. Sort on Server Side
**Pros:** Done once, sent sorted
**Cons:** MCP spec doesn't require it, can't rely on servers
**Not used:** Client-side sorting is more reliable

### 2. TableView with Sortable Columns
**Pros:** User can sort by clicking column headers
**Cons:** Overkill for simple name list, takes more space
**Not used:** ListView with default sort is simpler

### 3. Sort by Frequency/Recent Use
**Pros:** More useful tools appear first
**Cons:** Requires usage tracking, changes order dynamically
**Future enhancement:** Could add as preference

### 4. Manual Reordering
**Pros:** User controls exact order
**Cons:** Requires UI for drag-drop, state persistence
**Future enhancement:** Could add for power users

## Code Location

**Modified File:**
- `/jmcp-client/src/main/java/org/peacetalk/jmcp/client/McpClientController.java`
  - Method: `onConnect()`
  - Lines: ~113-117

## Testing

1. Run the client: `./run-client.sh`
2. Connect to a server
3. Verify tools are displayed in alphabetical order
4. Try disconnecting and reconnecting - order should be consistent

## Future Enhancements

Potential sorting improvements:

1. **Group by Category**
   - Database tools (query, describe, list-tables)
   - Admin tools (analyze, optimize)
   - Utility tools (export, import)

2. **Custom Sort Order**
   - User preference for sort order
   - Save sort preference per server

3. **Smart Sorting**
   - Frequently used tools first
   - Recently used tools first
   - Pinned favorites at top

4. **Search/Filter**
   - Filter tools by name
   - Search in descriptions
   - Maintain alphabetical order within filtered results

5. **Multi-Column Sort**
   - Primary: Category
   - Secondary: Alphabetical within category

## References

- [Java Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)
- [Stream.sorted()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#sorted(java.util.Comparator))
- [String.compareToIgnoreCase()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#compareToIgnoreCase(java.lang.String))

