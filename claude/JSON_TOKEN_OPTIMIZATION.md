# JSON Response Token Optimization for MCP

**Date:** December 29, 2025

## Why Optimize for Tokens?

1. **Cost Reduction** - LLMs charge per token (~$0.01-0.06 per 1K tokens)
2. **Context Window** - Less verbose = more room for conversation
3. **Speed** - Fewer tokens = faster responses
4. **Clarity** - Concise responses are often easier for LLMs to parse

## Current Response Analysis

### Example: Query Result (Verbose)

```json
{
  "content": [{
    "type": "text",
    "text": "{\"columns\":[{\"name\":\"ORDER_ID\",\"type\":\"INTEGER\"},{\"name\":\"CUSTOMER_NAME\",\"type\":\"VARCHAR\"},{\"name\":\"TOTAL\",\"type\":\"DECIMAL\"},{\"name\":\"ORDER_DATE\",\"type\":\"DATE\"}],\"rows\":[{\"ORDER_ID\":1,\"CUSTOMER_NAME\":\"Alice\",\"TOTAL\":100.00,\"ORDER_DATE\":\"2025-01-01\"},{\"ORDER_ID\":2,\"CUSTOMER_NAME\":\"Bob\",\"TOTAL\":200.00,\"ORDER_DATE\":\"2025-01-02\"}],\"rowCount\":2,\"hasMore\":false}"
  }]
}
```

**Token count:** ~120 tokens for 2 rows

---

## Optimization Strategies

### 1. Use Short Property Names

**Current:**
```json
{
  "columns": [...],
  "rowCount": 2,
  "hasMore": false,
  "customerName": "Alice"
}
```

**Optimized:**
```json
{
  "cols": [...],
  "cnt": 2,
  "more": false,
  "custName": "Alice"
}
```

**Savings:** ~20-30% token reduction

**Implementation:**
```java
public record QueryResult(
    @JsonProperty("cols") List<ColumnMetadata> columns,
    @JsonProperty("rows") List<Map<String, Object>> rows,
    @JsonProperty("cnt") int rowCount,
    @JsonProperty("more") boolean hasMore
) {}
```

### 2. Use Arrays Instead of Objects for Tabular Data

**Current (Objects):**
```json
{
  "rows": [
    {"ORDER_ID": 1, "CUSTOMER_NAME": "Alice", "TOTAL": 100.00},
    {"ORDER_ID": 2, "CUSTOMER_NAME": "Bob", "TOTAL": 200.00}
  ]
}
```

**Optimized (Arrays):**
```json
{
  "cols": ["ORDER_ID", "CUSTOMER_NAME", "TOTAL"],
  "rows": [
    [1, "Alice", 100.00],
    [2, "Bob", 200.00]
  ]
}
```

**Savings:** ~40-60% for large result sets!

**Why:**
- Column names repeated once, not per row
- No key-value separator `:` for each value
- No quotes around keys

**Token Comparison (100 rows, 5 columns):**
| Format | Approximate Tokens |
|--------|-------------------|
| Object per row | ~2,500 |
| Array per row | ~1,000 |

### 3. Omit Redundant Information

**Current:**
```json
{
  "table": "orders",
  "schema": "public",
  "columns": [...],
  "rows": [...],
  "rowCount": 10,
  "limit": 10
}
```

**Optimized:**
```json
{
  "t": "orders",
  "s": "public",
  "cols": [...],
  "rows": [...],
  "cnt": 10
}
```

**Why omit `limit`?**
- The LLM knows the limit from its own request
- `rowCount` tells how many rows returned
- If `rowCount < limit`, no more rows

### 4. Use Compact Column Metadata

**Current:**
```json
{
  "columns": [
    {"name": "ID", "type": "INTEGER", "size": null, "nullable": true, "defaultValue": null, "remarks": null},
    {"name": "NAME", "type": "VARCHAR", "size": 100, "nullable": false, "defaultValue": null, "remarks": null}
  ]
}
```

**Optimized (Array format):**
```json
{
  "cols": [
    ["ID", "INT", true],
    ["NAME", "VARCHAR(100)", false]
  ]
}
```

**Or with index-based headers:**
```json
{
  "colDef": ["name", "type", "nullable"],
  "cols": [
    ["ID", "INT", true],
    ["NAME", "VARCHAR(100)", false]
  ]
}
```

### 5. Abbreviate Common Values

**Type abbreviations:**
```java
private static final Map<String, String> TYPE_ABBREV = Map.of(
    "INTEGER", "INT",
    "VARCHAR", "STR",
    "DECIMAL", "DEC",
    "TIMESTAMP", "TS",
    "BOOLEAN", "BOOL"
);
```

**Boolean abbreviations:**
```json
// Instead of: {"nullable": true, "hasMore": false}
// Use: {"n": 1, "more": 0}
// Or: {"n": true, "more": false} // already compact
```

### 6. Omit Empty/Default Values

**Already implemented with `@JsonInclude(JsonInclude.Include.NON_NULL)`**

But could go further with `NON_DEFAULT`:
```java
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record QueryResult(
    List<ColumnMetadata> columns,
    List<List<Object>> rows,  // Array format
    int rowCount,
    boolean hasMore  // Omitted if false
) {}
```

### 7. Consider Binary/Compressed Formats (Advanced)

For very large result sets:
- **MessagePack** - Binary JSON, 50-80% smaller
- **CBOR** - Concise Binary Object Representation
- **Protobuf** - Google's Protocol Buffers

**Tradeoff:** LLM needs to decode, may not understand binary

---

## Recommended Implementation

### Option A: Compact Mode (Configurable)

Add a configuration option for compact responses:

```java
public class ResponseFormatter {
    private final boolean compact;
    
    public Object formatQueryResult(QueryResult result) {
        if (compact) {
            return new CompactQueryResult(
                result.columns().stream()
                    .map(c -> List.of(c.name(), abbreviateType(c.type())))
                    .toList(),
                result.rows().stream()
                    .map(row -> result.columns().stream()
                        .map(c -> row.get(c.name()))
                        .toList())
                    .toList(),
                result.rowCount(),
                result.hasMore()
            );
        }
        return result;
    }
}
```

### Option B: New Compact Result Records

```java
/**
 * Compact query result using arrays instead of objects.
 * 
 * Format:
 * {
 *   "cols": [["name", "type"], ...],
 *   "rows": [[val1, val2, ...], ...],
 *   "cnt": 10,
 *   "more": false
 * }
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record CompactQueryResult(
    @JsonProperty("cols") List<List<String>> columns,
    @JsonProperty("rows") List<List<Object>> rows,
    @JsonProperty("cnt") int rowCount,
    @JsonProperty("more") boolean hasMore
) {}
```

### Option C: Hybrid Approach

Keep verbose format for small results, use compact for large:

```java
public Object formatResult(QueryResult result) {
    if (result.rowCount() > 10) {
        return toCompact(result);
    }
    return result;  // Verbose OK for small results
}
```

---

## Token Savings Estimate

### Query with 50 rows, 6 columns

| Optimization | Tokens | Savings |
|--------------|--------|---------|
| Current (verbose) | ~1,500 | - |
| Short property names | ~1,200 | 20% |
| + Array rows | ~600 | 60% |
| + Compact columns | ~500 | 67% |
| + Omit defaults | ~450 | 70% |

**Potential savings: 70% token reduction!**

---

## Example Comparison

### Current Format (Verbose)

```json
{
  "content": [{
    "type": "text",
    "text": "{\"columns\":[{\"name\":\"id\",\"type\":\"INTEGER\"},{\"name\":\"name\",\"type\":\"VARCHAR\"},{\"name\":\"price\",\"type\":\"DECIMAL\"}],\"rows\":[{\"id\":1,\"name\":\"Widget\",\"price\":19.99},{\"id\":2,\"name\":\"Gadget\",\"price\":29.99},{\"id\":3,\"name\":\"Thing\",\"price\":39.99}],\"rowCount\":3,\"hasMore\":false}"
  }]
}
```

**~180 tokens**

### Optimized Format (Compact)

```json
{
  "content": [{
    "type": "text",
    "text": "{\"cols\":[[\"id\",\"INT\"],[\"name\",\"STR\"],[\"price\",\"DEC\"]],\"rows\":[[1,\"Widget\",19.99],[2,\"Gadget\",29.99],[3,\"Thing\",39.99]],\"cnt\":3}"
  }]
}
```

**~90 tokens (50% reduction!)**

---

## Implementation Recommendations

### Phase 1: Quick Wins (Easy)
1. ✅ Already using `@JsonInclude(NON_NULL)` - Good!
2. Add short property names with `@JsonProperty`
3. Omit redundant fields (like `limit` when it equals `rowCount`)

### Phase 2: Array Format (Medium)
4. Create parallel "compact" result classes
5. Add configuration option to enable compact mode
6. Update `JdbcToolUtils.extractRows()` to return arrays

### Phase 3: Advanced (Optional)
7. Type abbreviations
8. Threshold-based formatting (verbose for small, compact for large)
9. Consider MessagePack for very large results

---

## Caveats

### LLM Comprehension
- Some LLMs parse object format better than arrays
- Test with your target LLM to ensure it understands compact format
- Consider providing schema/legend in system prompt

### Debugging
- Compact format harder to read for humans
- Keep verbose format for development/debugging
- Make compact mode opt-in via configuration

### Backward Compatibility
- If clients expect specific format, versioning may be needed
- Consider `Accept` header or query parameter for format selection

---

## Sample Implementation Code

### Compact Result Records

```java
// New file: CompactQueryResult.java
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record CompactQueryResult(
    @JsonProperty("cols") List<List<String>> columns,
    @JsonProperty("rows") List<List<Object>> rows,
    @JsonProperty("cnt") int count,
    @JsonProperty("more") boolean hasMore
) {
    public static CompactQueryResult from(QueryResult verbose) {
        List<List<String>> cols = verbose.columns().stream()
            .map(c -> List.of(c.name(), abbreviate(c.type())))
            .toList();
            
        List<List<Object>> rows = verbose.rows().stream()
            .map(row -> verbose.columns().stream()
                .map(c -> row.get(c.name()))
                .toList())
            .toList();
            
        return new CompactQueryResult(cols, rows, verbose.rowCount(), verbose.hasMore());
    }
    
    private static String abbreviate(String type) {
        return switch (type.toUpperCase()) {
            case "INTEGER", "INT4", "INT8", "BIGINT", "SMALLINT" -> "INT";
            case "VARCHAR", "CHAR", "TEXT", "CLOB" -> "STR";
            case "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL" -> "NUM";
            case "TIMESTAMP", "DATETIME" -> "TS";
            case "DATE" -> "DT";
            case "BOOLEAN", "BOOL" -> "BOOL";
            default -> type.length() > 6 ? type.substring(0, 6) : type;
        };
    }
}
```

### Configuration Option

```java
// In config.json
{
  "connections": [...],
  "response_format": "compact"  // or "verbose"
}
```

---

## Summary

| Strategy | Effort | Token Savings | Recommended |
|----------|--------|---------------|-------------|
| Short property names | Low | 20% | ✅ Yes |
| Array rows | Medium | 40% | ✅ Yes |
| Compact columns | Medium | 10% | ✅ Yes |
| Type abbreviations | Low | 5% | Optional |
| Omit defaults | Low | 5% | ✅ Already done |
| Binary formats | High | 50%+ | For large data only |

**Total potential savings: 60-70%**

---

*"Brevity is the soul of wit."* - William Shakespeare

In JSON responses to LLMs: Brevity is the soul of tokens saved!

