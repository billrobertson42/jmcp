# Resource Simplification: Schema Tables & Views Embedding

**Date:** January 4, 2026  
**Change Type:** API Simplification & Optimization

---

## Summary

Removed separate `TablesListResource` and `ViewsListResource` and embedded table/view lists directly into `SchemaResource`. Also added schema URIs to the ContextResource summary for direct navigation.

---

## Motivation

### Problem 1: Unnecessary Navigation Hops
Previously, to get table/view information:
```
1. Read: db://connection/{id}/schema/{schema}
   → Get: links to tables and views collections
2. Read: db://connection/{id}/schema/{schema}/tables
   → Get: list of tables
3. Read: db://connection/{id}/schema/{schema}/table/{specific_table}
   → Get: table details
```

This required 3 requests when the middle request added no value.

### Problem 2: Missing Schema Links in Context
The ContextResource provided schema names but no URIs, requiring manual URI construction.

---

## Changes Made

### 1. Enhanced SchemaResource

**Before:**
```json
{
  "name": "public",
  "connectionId": "mydb",
  "tableCount": 15,
  "viewCount": 3,
  "links": {
    "parent": "db://connection/mydb/schemas",
    "tables": "db://connection/mydb/schema/public/tables",
    "views": "db://connection/mydb/schema/public/views",
    "relationships": "db://connection/mydb/schema/public/relationships"
  }
}
```

**After:**
```json
{
  "name": "public",
  "connectionId": "mydb",
  "tables": [
    {"name": "users", "uri": "db://connection/mydb/schema/public/table/users"},
    {"name": "orders", "uri": "db://connection/mydb/schema/public/table/orders"},
    ...
  ],
  "views": [
    {"name": "user_orders", "uri": "db://connection/mydb/schema/public/view/user_orders"}
  ],
  "links": {
    "parent": "db://connection/mydb/schemas",
    "relationships": "db://connection/mydb/schema/public/relationships"
  }
}
```

**Benefits:**
- ✅ Direct access to all table/view URIs in one request
- ✅ No need for intermediate collection resources
- ✅ Reduced round trips (3 requests → 2 requests)
- ✅ LLM can immediately navigate to specific tables

### 2. Enhanced ContextResource

**Before:**
```json
{
  "connections": [{
    "schemas": [
      {"name": "public", "isDefault": true, "tableCount": 15, ...}
    ]
  }]
}
```

**After:**
```json
{
  "connections": [{
    "schemas": [
      {
        "name": "public",
        "isDefault": true,
        "tableCount": 15,
        "schemaUri": "db://connection/mydb/schema/public"
      }
    ]
  }]
}
```

**Benefits:**
- ✅ LLM can directly navigate to schema details
- ✅ No manual URI construction required
- ✅ Consistent with embedded URIs pattern

### 3. Removed Resources

**Deleted:**
- `TablesListResource` (was at `db://connection/{id}/schema/{schema}/tables`)
- `ViewsListResource` (was at `db://connection/{id}/schema/{schema}/views`)

**Routing Updated:**
- `JdbcResourceProvider.handleObjectResource()` - Removed tables/views cases
- `ContextResource.getResourceInfo()` - Removed URI templates for these resources

**Tests Updated:**
- Removed `testTablesListResourceMetadata()` and `testTablesListResourceRead()`
- Removed `testViewsListResourceMetadata()` and `testViewsListResourceRead()`
- Updated `testSchemaResourceRead()` to check for tables/views arrays instead of counts

---

## New Navigation Patterns

### Pattern 1: Schema Overview → Direct Table Navigation
```
Read: db://context
→ Get schema URI from response
→ Read: db://connection/mydb/schema/public
→ Get table URI from response
→ Read: db://connection/mydb/schema/public/table/users
```

**Request Count:** 3 (down from 4)

### Pattern 2: Quick Table Discovery
```
Read: db://connection/mydb/schema/public
→ Response contains:
  - All table names with URIs
  - All view names with URIs
  - Relationship graph URI
```

**Request Count:** 1 (for discovery)

### Pattern 3: Complete Context with Navigation
```
Read: db://context
→ Response contains:
  - Schema names with URIs
  - Sample table names (first 20)
  - All tool information
  - All resource URI templates
```

**Request Count:** 1 (for complete overview)

---

## Performance Impact

### Reduced Round Trips
- **Before:** 4 requests (context → schema → tables list → specific table)
- **After:** 3 requests (context → schema → specific table)
- **Improvement:** 25% reduction

### Increased Response Size
- Schema resource now includes full table/view lists
- For schema with 100 tables: ~5KB instead of ~500 bytes
- Acceptable tradeoff for eliminating round trip

### Database Queries
- **Before:** 2 queries (count tables, count views)
- **After:** 2 queries (list tables, list views)
- **Impact:** Same number of queries, but now return names instead of counts

---

## API Breaking Changes

### ⚠️ Breaking: Schema Resource Response Structure

**Old Schema Response:**
```json
{
  "tableCount": 15,
  "viewCount": 3,
  "links": { "tables": "...", "views": "..." }
}
```

**New Schema Response:**
```json
{
  "tables": [{"name": "...", "uri": "..."}],
  "views": [{"name": "...", "uri": "..."}],
  "links": { /* no tables/views links */ }
}
```

**Migration:** Clients using `tableCount`/`viewCount` should use `tables.length`/`views.length`

### ⚠️ Breaking: Removed Resources

These URIs now return null/404:
- `db://connection/{id}/schema/{schema}/tables`
- `db://connection/{id}/schema/{schema}/views`

**Migration:** Use `db://connection/{id}/schema/{schema}` instead

### ✅ Non-Breaking: Context Resource

Added `schemaUri` field to schema summaries. Backward compatible - old clients can ignore.

---

## Files Modified

1. **SchemaResource.java**
   - Changed from counts to lists
   - Added TableLink and ViewLink records
   - Removed tables/views navigation links

2. **ContextResource.java**
   - Added schemaUri to SchemaSummary record
   - Updated URI template descriptions
   - Removed templates for tables/views list resources

3. **JdbcResourceProvider.java**
   - Removed routing for tables/views list resources
   - Simplified handleObjectResource()

4. **JdbcResourcesTest.java**
   - Updated schema resource tests
   - Removed tables/views list resource tests
   - Reduced test count from 22 → 18

5. **resource_enhancements_jan_2026.md**
   - Updated resource hierarchy diagram
   - Noted removal of list resources

---

## Rationale

### Why Remove List Resources?

1. **No Added Value:** They just wrapped lists that could be in schema resource
2. **Unnecessary Hop:** Required extra request with no additional data
3. **Inconsistent:** Other resources embed lists (ConnectionsListResource has embedded connections)
4. **Complexity:** More resources = more code, tests, documentation

### Why Not Remove Other List Resources?

- **ConnectionsListResource:** Root-level resource, different pattern
- **SchemasListResource:** Could potentially be removed, but less critical (fewer schemas than tables)

### Future Consideration

Could also embed schema list into ConnectionResource, but:
- Connections might have many schemas (100+)
- Less common to need all schema details at once
- Keep for now, revisit if usage patterns show it's unnecessary

---

## Testing

### Test Coverage
- ✅ Schema resource returns tables/views arrays
- ✅ Each table/view has name and uri fields
- ✅ URIs are correctly formatted
- ✅ Context resource includes schemaUri

### Test Count Change
- **Before:** 22 resource tests
- **After:** 18 resource tests
- **Removed:** 4 tests (2 for tables list, 2 for views list)

---

## Documentation Impact

### Updated Documentation
1. Context resource URI template list (removed 2 templates)
2. Resource hierarchy diagram (simplified)
3. Navigation pattern examples (updated request sequences)

### Runtime Documentation
The ContextResource now correctly reflects:
- Schema resource provides direct table/view access
- No mention of intermediate list resources
- Clear navigation path from schema to table

---

## Benefits Summary

| Benefit | Impact |
|---------|--------|
| **Reduced Requests** | 25% fewer round trips for common workflows |
| **Simpler API** | 2 fewer resource types to understand |
| **Direct Navigation** | LLMs get all URIs in one response |
| **Less Code** | ~200 lines removed (resources + tests) |
| **Better UX** | More information per request |

---

## Risks & Mitigation

### Risk 1: Large Schemas
**Issue:** Schema with 1000+ tables = large response

**Mitigation:**
- Acceptable for most cases (schemas rarely have 1000+ tables)
- Could add pagination if needed (query param: `?page=1&limit=100`)
- Resources are cacheable

### Risk 2: Breaking Change
**Issue:** Existing clients may expect old structure

**Mitigation:**
- This is a preview/development system
- No production clients yet
- Clear migration path documented

---

## Conclusion

Removing intermediate list resources simplifies the API and improves performance by eliminating unnecessary round trips. The schema resource now provides complete, directly navigable information about tables and views in a single request.

This change makes the resource API more intuitive for LLMs while reducing the total number of resource types from 11 to 9.

**Recommendation:** This simplification should be considered standard practice for future resource design - embed lists when they add no additional context beyond navigation.

