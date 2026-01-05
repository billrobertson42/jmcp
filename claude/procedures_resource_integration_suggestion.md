# Procedures Resource Integration Suggestions

**Date:** January 4, 2026

---

## Current State

The `list-procedures` tool provides:
- List of stored procedures and functions
- Parameters (name, type, mode: IN/OUT/INOUT/RETURN)
- Return type (for functions)
- Definition (source code)
- Language (SQL, plpgsql, etc.)
- Determinism flag
- Remarks/comments

---

## Suggestion: Resource Model Integration

### Option 1: Schema-Level Procedures Resource

**URI Pattern:**
```
db://connection/{id}/schema/{schema}/procedures
```

**Returns:**
```json
{
  "schema": "public",
  "procedures": [
    {
      "name": "calculate_total",
      "type": "FUNCTION",
      "returnType": "NUMERIC",
      "uri": "db://connection/{id}/schema/{schema}/procedure/calculate_total"
    }
  ],
  "functions": [
    {
      "name": "get_user_orders",
      "type": "FUNCTION",
      "returnType": "TABLE",
      "uri": "db://connection/{id}/schema/{schema}/procedure/get_user_orders"
    }
  ],
  "count": 2,
  "links": {
    "parent": "db://connection/{id}/schema/{schema}"
  }
}
```

### Option 2: Individual Procedure Resource

**URI Pattern:**
```
db://connection/{id}/schema/{schema}/procedure/{name}
```

**Returns:**
```json
{
  "name": "calculate_total",
  "schema": "public",
  "type": "FUNCTION",
  "parameters": [
    {"name": "order_id", "type": "INTEGER", "mode": "IN", "position": 1},
    {"name": "include_tax", "type": "BOOLEAN", "mode": "IN", "position": 2}
  ],
  "returnType": "NUMERIC",
  "language": "plpgsql",
  "isDeterministic": false,
  "definition": "CREATE OR REPLACE FUNCTION calculate_total...",
  "remarks": "Calculates order total with optional tax",
  "links": {
    "parent": "db://connection/{id}/schema/{schema}/procedures"
  }
}
```

---

## Integration Points

### 1. Modify SchemaResource

Add procedures/functions summary to existing SchemaResource:

```json
{
  "name": "public",
  "tables": [...],
  "views": [...],
  "procedures": [
    {"name": "calculate_total", "type": "FUNCTION", "uri": "..."},
    {"name": "process_order", "type": "PROCEDURE", "uri": "..."}
  ],
  "procedureCount": 2,
  "links": {
    "procedures": "db://connection/{id}/schema/{schema}/procedures"
  }
}
```

### 2. Add to ContextResource

Include procedure counts in connection summary:

```json
{
  "connections": [
    {
      "id": "prod",
      "schemas": [
        {
          "name": "public",
          "tableCount": 15,
          "viewCount": 3,
          "procedureCount": 8
        }
      ]
    }
  ]
}
```

---

## Resource vs Tool Trade-offs

### Keep as Tool (Current)

**Pros:**
- Dynamic - definitions can change
- Parameters allow filtering
- includeDetails flag controls response size

**Cons:**
- Not cacheable
- No navigation
- No HATEOAS links

### Move to Resource

**Pros:**
- Cacheable (metadata rarely changes)
- Navigable via URIs
- Consistent with tables/views pattern
- HATEOAS links to related procedures

**Cons:**
- Procedure definitions can be large
- May need lazy loading for definitions

---

## Recommended Approach

### Hybrid Model

1. **Add ProceduresListResource**
   - URI: `db://connection/{id}/schema/{schema}/procedures`
   - Returns: List with names, types, return types, and URIs
   - Lightweight, cacheable

2. **Add ProcedureResource**
   - URI: `db://connection/{id}/schema/{schema}/procedure/{name}`
   - Returns: Full details including definition
   - Detailed, cached individually

3. **Keep list-procedures Tool**
   - For bulk operations
   - For filtering by pattern
   - For includeDetails flag flexibility

4. **Enhance SchemaResource**
   - Add procedure list summary (names + URIs only)
   - Add link to procedures resource

---

## Implementation Files Needed

```
jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/
├── ProceduresListResource.java    # New
├── ProcedureResource.java         # New
├── results/
│   └── ProcedureInfo.java         # Already exists (may need update)
```

---

## URI Scheme Summary

```
db://
└── connection/{id}/
    └── schema/{schema}/
        ├── tables              # Embedded in SchemaResource
        ├── table/{table}       # TableResource
        ├── views               # Embedded in SchemaResource
        ├── view/{view}         # ViewResource
        ├── procedures          # NEW: ProceduresListResource
        ├── procedure/{name}    # NEW: ProcedureResource
        └── relationships       # SchemaRelationshipsResource
```

---

## Questions to Consider

1. **Naming:** Should we distinguish between procedures and functions?
   - Separate: `/procedures` and `/functions`
   - Combined: `/procedures` (with type field)
   - Combined (chosen): simpler, type field provides distinction

2. **Definition Size:** Procedure definitions can be very large
   - Option A: Always include definition
   - Option B: Separate endpoint for definition only
   - Option C: Include definition, rely on client caching

3. **Overloaded Procedures:** Same name, different signatures
   - Option A: Include signature in URI (complex)
   - Option B: Return array for same name
   - Option C: Use OID or unique identifier

4. **Security:** Procedure definitions may contain sensitive logic
   - Option A: Always show definitions
   - Option B: Config flag to hide definitions
   - Option C: Respect database permissions

---

## Conclusion

The suggested approach adds:
- `ProceduresListResource` for navigable procedure lists
- `ProcedureResource` for individual procedure details
- Enhanced `SchemaResource` with procedure summary
- Keep `list-procedures` tool for flexibility

This maintains consistency with the existing resource model (tables, views) while preserving the tool for advanced use cases.

