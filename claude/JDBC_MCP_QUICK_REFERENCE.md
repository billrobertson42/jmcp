# JDBC MCP - Quick Reference (January 11, 2026)

## Current Tools (5 - All Dynamic)

| Tool | Purpose | Key Parameters |
|------|---------|----------------|
| **query** | Execute SELECT queries | sql, parameters, validate_only, database_id |
| **explain-query** | Get query execution plan | sql, database_id |
| **get-row-count** | Get exact table row count | table, schema, database_id |
| **sample-data** | Sample table data | table, schema, sample_size, strategy, columns |
| **analyze-column** | Analyze column distribution | table, column, schema, top_values |

## Resource Structure

```
db://
├── context                                    # Complete overview
├── connections                                # List all connections
└── connection/{id}/
    ├── (self)                                 # Connection details
    ├── relationships                          # Database-wide FK graph
    ├── schemas                                # List all schemas
    └── schema/{schema}/
        ├── (self)                             # Tables, views, procedures (inline)
        ├── relationships                      # Schema-specific FK graph
        ├── table/{table}                      # Full table structure
        ├── view/{view}                        # View definition
        └── procedure/{proc}                   # (Future) Procedure details
```

## Schema Resource Response

```json
{
  "name": "public",
  "connectionId": "mydb",
  "tables": [
    {"name": "users", "uri": "db://connection/mydb/schema/public/table/users"},
    {"name": "orders", "uri": "db://..."}
  ],
  "views": [
    {"name": "active_users", "uri": "db://..."}
  ],
  "procedures": [
    {"name": "calculate_total", "type": "FUNCTION", "uri": "db://..."},
    {"name": "update_stats", "type": "PROCEDURE", "uri": "db://..."}
  ],
  "links": {
    "parent": "db://connection/mydb/schemas",
    "relationships": "db://connection/mydb/schema/public/relationships"
  }
}
```

## Context Resource Response

```json
{
  "connections": [
    {
      "databaseId": "mydb",
      "databaseType": "postgresql",
      "schemas": [
        {
          "name": "public",
          "isDefault": true,
          "tableCount": 15,
          "viewCount": 3,
          "procedureCount": 7,
          "tables": ["users", "orders", "products", ...],
          "views": ["active_users", "recent_orders", ...],
          "procedures": ["calculate_total", "get_stats", ...],
          "uri": "db://connection/mydb/schema/public"
        }
      ]
    }
  ],
  "availableTools": [...],
  "resources": {...},
  "usageHints": [...]
}
```

## Common LLM Patterns

### 1. Initial Discovery
```
Read: db://context
→ Get complete database overview
```

### 2. Explore Schema
```
Read: db://connection/{id}/schema/{schema}
→ Get all tables, views, procedures with URIs
```

### 3. Table Details
```
Read: db://connection/{id}/schema/{schema}/table/{table}
→ Get columns, PKs, FKs (both ways), indexes
```

### 4. Relationship Analysis
```
Read: db://connection/{id}/relationships
→ Get complete FK graph for database

Read: db://connection/{id}/schema/{schema}/relationships
→ Get FK graph for specific schema
```

### 5. Query Data
```
Tool: query
Params: { "sql": "SELECT ...", "database_id": "mydb" }
→ Execute query, get up to 1000 rows
```

### 6. Analyze Column
```
Tool: analyze-column
Params: { "table": "users", "column": "status", "schema": "public" }
→ Get distinct values, nulls, distribution
```

## Design Principles

1. **Resources = Metadata** (cacheable, structural)
2. **Tools = Operations** (dynamic, transient)
3. **No Redundancy** (one way to access metadata)
4. **Token Efficient** (no count fields, compact responses)
5. **HATEOAS** (navigation via URIs)

## Tools Removed (Now Resources)

- ❌ `list-tables` → Use SchemaResource
- ❌ `list-schemas` → Use SchemasListResource
- ❌ `list-procedures` → Use SchemaResource
- ❌ `list-connections` → Use ConnectionsListResource (future)
- ❌ `describe-table` → Use TableResource (future)

## Token Optimization

- No `count` fields (derivable from array.length)
- Procedures in schema (no separate call)
- Compact JSON (no redundant nesting)
- Context provides overview (fewer round trips)

## Future Enhancements

1. ~~Individual procedure resources~~ ✅ IMPLEMENTED
2. Index detail resources
3. Constraint detail resources
4. Table statistics in resources
5. Extended column information

