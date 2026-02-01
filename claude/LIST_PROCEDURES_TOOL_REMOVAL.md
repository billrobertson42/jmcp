# ListProceduresTool Removal and Context Resource Update

**Date:** January 11, 2026

## Summary

Removed the ListProceduresTool since procedures are now available via the SchemaResource, and updated the ContextResource to reflect this change.

## Changes Made

### 1. Removed ListProceduresTool

- **Deleted:** `ListProceduresTool.java`
  - Tool was redundant with SchemaResource which now includes procedures
  - Procedures are accessible via `db://connection/{id}/schema/{schema}`

- **Deleted:** `ListProceduresToolTest.java`
  - Test for deleted tool

### 2. Updated ContextResource

- **Modified:** `ContextResource.java`
  - Added procedure counting to schema iteration
  - Added `procedureCount` field to `SchemaSummary` record
  - Added `procedures` list field to `SchemaSummary` record (limited to 10 names)
  - Updated schema resource template description to mention procedures
  - Updated JavaDoc for `SchemaSummary` to reflect procedures

**Before:**
```java
public record SchemaSummary(
    String name,
    boolean isDefault,
    int tableCount,
    int viewCount,
    List<String> tables,
    List<String> views,
    String uri
) {}
```

**After:**
```java
public record SchemaSummary(
    String name,
    boolean isDefault,
    int tableCount,
    int viewCount,
    int procedureCount,
    List<String> tables,
    List<String> views,
    List<String> procedures,
    String uri
) {}
```

### 3. Kept Supporting Classes

- **Kept:** `ProcedureInfo.java` - May be useful for future procedure resources
- **Kept:** `ProcedureParameter.java` - May be useful for future procedure resources
- **Kept:** `ProceduresListResult.java` - May be useful for future procedure resources

## Why Remove ListProceduresTool?

1. **Redundancy**: SchemaResource now provides the same information
2. **Consistency**: Tables, views, and procedures all accessed the same way
3. **Token Efficiency**: No need for separate tool call
4. **Better UX**: Single navigation model via resources

## Context Resource Output

The context resource now shows procedures alongside tables and views:

```json
{
  "connections": [
    {
      "databaseId": "mydb",
      "schemas": [
        {
          "name": "public",
          "isDefault": true,
          "tableCount": 15,
          "viewCount": 3,
          "procedureCount": 7,
          "tables": ["users", "orders", ...],
          "views": ["active_users", ...],
          "procedures": ["calculate_total", "get_user_stats", ...],
          "uri": "db://connection/mydb/schema/public"
        }
      ]
    }
  ],
  "resources": {
    "uriTemplates": [
      {
        "uriPattern": "db://connection/{database_id}/schema/{schema_name}",
        "name": "Schema details",
        "description": "Schema with lists of all tables, views, and procedures (includes URIs for direct navigation)"
      },
      ...
    ]
  }
}
```

## LLM Benefits

1. **Discovery**: LLMs can see procedure counts in context overview
2. **Navigation**: Procedures accessible via schema resource like tables/views
3. **Consistency**: Same pattern for all schema objects
4. **Efficiency**: One resource request shows all schema contents

## Migration Notes

- The `list-procedures` tool is no longer available
- Use `db://connection/{id}/schema/{schema}` resource instead
- Procedure details (parameters, definitions) can be accessed via individual procedure URIs when needed (future enhancement)

## Comparison with Previous State

### Before (3 separate operations)
1. Call `list-tables` tool → Get table list
2. Call `list-views` tool → Get view list  
3. Call `list-procedures` tool → Get procedure list

### After (1 resource read)
1. Read `db://connection/{id}/schema/{schema}` resource → Get tables, views, and procedures

**Token Savings:** ~40-60 tokens per schema inspection
**Latency Reduction:** 2 fewer round trips

## Related Changes

This change builds on previous work:
- Tables moved to SchemaResource (see SCHEMA_PROCEDURES_AND_COUNT_REMOVAL.md)
- Views moved to SchemaResource (see SCHEMA_PROCEDURES_AND_COUNT_REMOVAL.md)
- Procedures added to SchemaResource (see SCHEMA_PROCEDURES_AND_COUNT_REMOVAL.md)
- Count fields removed from all resources (see SCHEMA_PROCEDURES_AND_COUNT_REMOVAL.md)

## Future Enhancements

Consider adding individual procedure resources in the future:
- `db://connection/{id}/schema/{schema}/procedure/{procedure}`
- Would include parameters, definition, language, determinism
- Would match pattern of table and view resources

