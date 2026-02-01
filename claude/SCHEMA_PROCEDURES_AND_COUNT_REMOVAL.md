# Schema Procedures Integration and Count Field Removal

**Date:** January 11, 2026

## Summary

Updated the resource model to include procedures in schema listings and removed unnecessary count fields from all resource responses to reduce token usage.

## Changes Made

### 1. Added Procedures to SchemaResource

- **Modified:** `SchemaResource.java`
  - Added procedures list alongside tables and views
  - Fetches procedures using `DatabaseMetaData.getProcedures()`
  - Includes procedure type (FUNCTION/PROCEDURE) in the response
  - Added `ProcedureLink` record with name, type, and URI
  - Updated description to mention procedures

- **Modified:** `Util.java`
  - Added `procedureUri()` method to generate procedure URIs
  - Pattern: `db://connection/{id}/schema/{schema}/procedure/{name}`

### 2. Removed Count Fields

Removed redundant `count` fields from all resource responses as they:
- Waste tokens (not useful information)
- Can be derived by counting array entries
- Don't provide value to LLMs

**Files Modified:**
- `ConnectionsListResource.java` - Removed count from ConnectionsResponse
- `SchemasListResource.java` - Removed count from SchemasResponse  
- `TablesListResource.java` - Removed count from TablesResponse
- `ViewsListResource.java` - Removed count from ViewsResponse

### 3. Updated Tests

- **Modified:** `JdbcResourcesTest.java`
  - Added procedures field verification to `testSchemaResourceRead()`
  - Removed count field assertions from:
    - `testConnectionsListResourceRead()`
    - `testSchemasListResourceRead()`
  - Replaced count checks with array size checks where needed

- **Deleted:** `ListViewsToolTest.java`
  - Test was for deleted ListViewsTool
  - Views are now exposed via SchemaResource

## Schema Response Structure

A schema resource now returns:

```json
{
  "name": "schema_name",
  "connectionId": "connection_id",
  "tables": [
    {"name": "table1", "uri": "db://..."}
  ],
  "views": [
    {"name": "view1", "uri": "db://..."}
  ],
  "procedures": [
    {"name": "proc1", "type": "PROCEDURE|FUNCTION", "uri": "db://..."}
  ],
  "links": {
    "parent": "db://...",
    "relationships": "db://..."
  }
}
```

## Benefits

1. **Unified Schema View**: All schema objects (tables, views, procedures) accessible from one resource
2. **Token Efficiency**: Removed count fields save ~10-20 tokens per response
3. **Consistency**: All list-style resources follow the same pattern
4. **HATEOAS Navigation**: Procedures can be navigated via URIs like other schema objects

## Migration Notes

- Clients should no longer expect `count` fields in resource responses
- Count can be obtained by checking array `.length` property
- Procedures are now discoverable via schema resources instead of requiring separate ListProceduresTool

