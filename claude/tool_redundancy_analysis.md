# Tool Redundancy Analysis

**Date:** January 4, 2026

## Current Tool Suite

1. ✅ **list-connections** - Lists database connections
2. ✅ **query** - Execute SELECT queries
3. ❌ **list-tables** - List tables in a schema
4. ❌ **list-schemas** - List schemas in a database
5. ❓ **describe-table** - Describe table structure
6. ✅ **get-row-count** - Get exact row count
7. ✅ **get-table-statistics** - Get table statistics (size, rows)
8. ✅ **sample-data** - Sample data from table
9. ✅ **analyze-column** - Analyze column distribution

## Current Resource Structure

```
db://
├── context                           # Complete overview (all connections, schemas, tools, resources)
├── connections                       # List all connections
└── connection/{id}/
    ├── (self)                        # Connection details
    ├── relationships                 # Database-wide FK graph
    ├── schemas                       # List schemas
    └── schema/{schema}/
        ├── (self)                    # Schema with embedded tables/views lists + URIs
        ├── relationships             # Schema-specific FK graph
        ├── table/{table}             # Table structure (columns, PKs, FKs both ways, indexes)
        └── view/{view}               # View definition with SQL
```

---

## Redundancy Analysis

### ❌ **REDUNDANT: list-tables**

**Current Tool:**
```
Tool: list-tables
Params: { "schema": "public" }
Result: { "tables": ["users", "orders", ...], "count": 10 }
```

**Equivalent Resource:**
```
Resource: db://connection/{id}/schema/public
Result: {
  "tables": [
    {"name": "users", "uri": "db://..."},
    {"name": "orders", "uri": "..."}
  ],
  ...
}
```

**Why Redundant:**
- SchemaResource now includes complete table list with URIs
- No additional information provided by tool
- Resource is cacheable, tool is not
- Resource provides navigation links, tool does not

**Recommendation:** ✅ **REMOVE**

---

### ❌ **REDUNDANT: list-schemas**

**Current Tool:**
```
Tool: list-schemas
Params: { "database_id": "mydb" }
Result: { "schemas": ["public", "auth", ...], "count": 3 }
```

**Equivalent Resource:**
```
Resource: db://connection/mydb/schemas
Result: {
  "schemas": [
    {"name": "public", "isDefault": true, "tableCount": 10, ...},
    {"name": "auth", ...}
  ],
  ...
}
```

**Alternative Resource:**
```
Resource: db://connection/mydb
Result: {
  "schemas": [...],  // Same as above
  "links": {"schemas": "db://..."}
}
```

**Why Redundant:**
- Both ConnectionResource and SchemasListResource provide schema lists
- Resources provide more information (isDefault, counts)
- Resources provide navigation URIs
- Resources are cacheable

**Recommendation:** ✅ **REMOVE**

---

### ❓ **MAYBE REDUNDANT: describe-table**

**Current Tool:**
```
Tool: describe-table
Params: { 
  "table": "users",
  "includeTriggers": true,
  "includeCheckConstraints": true,
  ...
}
Result: { columns, primaryKeys, foreignKeys, indexes, triggers, ... }
```

**Equivalent Resource:**
```
Resource: db://connection/{id}/schema/public/table/users
Result: {
  "columns": [...],
  "primaryKey": [...],
  "foreignKeys": [...],
  "reverseForeignKeys": [...],  // BETTER than tool!
  "indexes": [...]
}
```

**What Tool Has That Resource Doesn't:**
- ⚠️ **Triggers** (optional flag)
- ⚠️ **Check Constraints** (optional flag)
- ⚠️ **Table Statistics** (optional flag) - Now separate tool
- ⚠️ **Partition Info** (optional flag)
- ⚠️ **Generated Columns** (optional flag)

**What Resource Has That Tool Doesn't:**
- ✅ **Reverse Foreign Keys** (tables that reference this table)
- ✅ **Navigation URIs** (HATEOAS)
- ✅ **ON DELETE/ON UPDATE** rules

**Analysis:**
- Core functionality (95% use case): **Fully covered by resource**
- Optional features (5% use case): **Only in tool**
- Resource provides better FK information

**Recommendation:** ⚠️ **KEEP FOR NOW**

**Rationale:**
1. Optional features (triggers, check constraints) are database-specific and rarely needed
2. If we add these to resource later, we can deprecate tool then
3. Resource already handles 95% of use cases
4. Having both allows gradual migration

**Future:** Consider adding triggers/check constraints to TableResource, then deprecate tool

---

## Tools That Should STAY

### ✅ **list-connections** - KEEP

**Why:**
- No resource equivalent (yet)
- Could be replaced by `db://connections` resource (already exists!)
- **Actually, this MIGHT be redundant too!**

Let me check...

**Tool:**
```
Tool: list-connections
Result: { connections: [{"id": "prod", "type": "postgresql", ...}] }
```

**Resource:**
```
Resource: db://connections
Result: { connections: [{"id": "prod", "type": "postgresql", ...}] }
```

**Status:** ❓ **POSSIBLY REDUNDANT** - Need to verify they return same data

---

### ✅ **query** - KEEP

**Why:**
- Dynamic operation (executes parameterized SELECT)
- Results change constantly
- Not cacheable
- Cannot be a resource

---

### ✅ **get-row-count** - KEEP

**Why:**
- Dynamic operation (count changes with data)
- Point-in-time result
- Not cacheable
- Exact count (vs approximate in statistics)

---

### ✅ **get-table-statistics** - KEEP

**Why:**
- Dynamic operation (statistics change)
- Point-in-time metrics
- Not cacheable
- Operational data, not structural metadata

---

### ✅ **sample-data** - KEEP

**Why:**
- Dynamic operation (samples actual data)
- Different strategies (random, first, last)
- Not cacheable
- Data values, not metadata

---

### ✅ **analyze-column** - KEEP

**Why:**
- Computed statistics (requires aggregation)
- Point-in-time analysis
- Expensive operation
- Not cacheable
- Data profiling, not structure

---

## Summary of Recommendations

### Remove Immediately (Clear Redundancy)

| Tool | Reason | Replacement |
|------|--------|-------------|
| ❌ **list-tables** | SchemaResource includes tables | `db://connection/{id}/schema/{schema}` |
| ❌ **list-schemas** | SchemasListResource exists | `db://connection/{id}/schemas` |

**Lines of Code to Remove:** ~200-300 lines + tests

---

### Investigate Further

| Tool | Question | Action |
|------|----------|--------|
| ❓ **list-connections** | Does it match ConnectionsListResource? | Compare outputs, possibly remove |

---

### Keep For Now (Until Resource Enhanced)

| Tool | Keep Because | Future |
|------|--------------|--------|
| ⚠️ **describe-table** | Optional features (triggers, checks) | Add to resource, then deprecate |

---

### Definitely Keep (Dynamic Operations)

| Tool | Type | Reason |
|------|------|--------|
| ✅ **query** | Dynamic | Executes user queries |
| ✅ **get-row-count** | Dynamic | Point-in-time count |
| ✅ **get-table-statistics** | Dynamic | Operational metrics |
| ✅ **sample-data** | Dynamic | Actual data sampling |
| ✅ **analyze-column** | Dynamic | Computed statistics |

---

## Migration Strategy

### Phase 1: Remove Clear Redundancies ✅ **DO NOW**

1. Remove `ListTablesTool`
2. Remove `ListSchemasTool`
3. Update tool provider registration
4. Update documentation to use resources
5. Remove tests for deleted tools

**Estimated Effort:** 30 minutes  
**Risk:** Low (resources fully tested)  
**Breaking Change:** Yes, but expected in development

---

### Phase 2: Investigate list-connections ⏳ **NEXT**

1. Compare `ListConnectionsTool` output with `ConnectionsListResource`
2. If identical, remove tool
3. Update documentation

**Estimated Effort:** 15 minutes  
**Risk:** Very low

---

### Phase 3: Enhance describe-table Resource ⏸️ **LATER**

1. Add triggers to TableResource (optional)
2. Add check constraints to TableResource (optional)
3. Add partition info to TableResource (optional)
4. Deprecate `DescribeTableTool`
5. Remove tool in next major version

**Estimated Effort:** 2-3 hours  
**Risk:** Medium (database-specific queries)  
**Breaking Change:** Only when tool is removed

---

## Code Changes Required

### 1. Remove ListTablesTool

**Files to Delete:**
- `src/main/java/org/peacetalk/jmcp/jdbc/tools/ListTablesTool.java`
- `src/test/java/test/org/peacetalk/jmcp/jdbc/tools/ListTablesToolTest.java` (if exists)

**Files to Modify:**
- `JdbcToolProvider.java` - Remove registration

---

### 2. Remove ListSchemasTool

**Files to Delete:**
- `src/main/java/org/peacetalk/jmcp/jdbc/tools/ListSchemasTool.java`
- `src/test/java/test/org/peacetalk/jmcp/jdbc/tools/ListSchemasToolTest.java` (if exists)

**Files to Modify:**
- `JdbcToolProvider.java` - Remove registration

---

## Documentation Updates

### Tools Documentation
- Remove list-tables from tool reference
- Remove list-schemas from tool reference
- Update "How to list tables" to use resources
- Update "How to list schemas" to use resources

### Resource Documentation
- Emphasize that SchemaResource includes table/view lists
- Show examples of using resources instead of tools

### Migration Guide
- Create guide for users who were using old tools
- Show resource equivalents

---

## Impact Analysis

### For LLMs
- ✅ **Better** - Resources are cacheable, reducing repeated queries
- ✅ **Better** - Navigation links make exploration easier
- ✅ **Better** - More semantic (URIs are self-documenting)
- ✅ **Simpler** - Fewer tools to learn

### For Developers
- ✅ **Less Code** - Fewer tools to maintain
- ✅ **Clearer Architecture** - Resources for metadata, tools for operations
- ❌ **Breaking Change** - Need to update existing code

### For Users
- ❌ **Breaking Change** - Tool calls will fail
- ✅ **Better Long-term** - Resources are more appropriate
- ⚠️ **Learning Curve** - Need to understand resource model

---

## Testing Strategy

### Before Removal
1. ✅ Verify resources provide equivalent functionality
2. ✅ Confirm resources are tested
3. ✅ Check resource performance acceptable

### After Removal
1. ⚠️ Remove tool tests
2. ✅ Verify tool provider starts without errors
3. ✅ Test resource equivalents work
4. ✅ Update integration tests

---

## Recommendation: Proceed

### Remove Now
- ✅ `ListTablesTool`
- ✅ `ListSchemasTool`

### Verify Then Decide
- ❓ `ListConnectionsTool`

### Keep
- ✅ All dynamic operation tools
- ⚠️ `DescribeTableTool` (for now)

**Total Tools After Cleanup:** 7-8 (down from 9)

**Next Action:** Shall I proceed with removing `ListTablesTool` and `ListSchemasTool`?

