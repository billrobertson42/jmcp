# Procedure Resource Implementation

**Date:** January 11, 2026

## Overview

Added ProcedureResource to provide detailed information about individual stored procedures and functions, completing the resource-based metadata access pattern for all database objects.

## Changes Made

### 1. Created ProcedureResource

**File:** `ProcedureResource.java`

Provides comprehensive procedure/function metadata:
- **Basic Info**: name, schema, type (FUNCTION/PROCEDURE/UNKNOWN)
- **Parameters**: name, type, mode (IN/OUT/INOUT/RETURN), position, length, precision, scale
- **Return Type**: For functions, the return data type
- **Definition**: SQL source code (database-specific queries)
- **Language**: Procedure language (e.g., SQL, plpgsql, plpython)
- **Determinism**: Whether procedure is deterministic
- **Remarks**: Documentation/comments
- **Navigation**: Link back to parent schema

**URI Pattern:** `db://connection/{id}/schema/{schema}/procedure/{name}`

### 2. Updated JdbcResourceProvider

Added procedure case to resource routing:
```java
case "procedure" -> new ProcedureResource(connectionId, schemaName, objectName, connectionManager);
```

### 3. Updated ContextResource

Added procedure resource template:
```json
{
  "uriPattern": "db://connection/{database_id}/schema/{schema_name}/procedure/{procedure_name}",
  "name": "Procedure/Function details",
  "description": "Parameters, return type, definition, language, determinism"
}
```

### 4. Created Tests

**File:** `ProcedureResourceTest.java`

Tests:
- Resource metadata (URI, name, description, MIME type)
- Resource read with actual procedure data
- Non-existent procedure handling

## Database-Specific Features

### PostgreSQL
- **Definition**: Retrieved via `pg_get_functiondef()`
- **Language**: Extracted from `pg_language` catalog
- **Determinism**: Checked via `provolatile` attribute

### MySQL/MariaDB
- **Definition**: From `information_schema.ROUTINES`
- **Language**: Always "SQL"
- **Determinism**: From `IS_DETERMINISTIC` field

### Oracle
- **Definition**: Assembled from `ALL_SOURCE` line by line
- **Language**: Not available
- **Determinism**: Not available

### SQL Server
- **Definition**: Via `OBJECT_DEFINITION()` function
- **Language**: Not available
- **Determinism**: Not available

### H2
- **Definition**: Limited support (aliases)
- **Parameters**: Basic metadata available
- **Language**: Not available

## Response Structure

```json
{
  "name": "calculate_total",
  "schema": "public",
  "connectionId": "mydb",
  "type": "FUNCTION",
  "parameters": [
    {
      "name": "order_id",
      "type": "INTEGER",
      "mode": "IN",
      "position": 1,
      "length": null,
      "precision": 10,
      "scale": 0
    },
    {
      "name": "tax_rate",
      "type": "DECIMAL",
      "mode": "IN",
      "position": 2,
      "length": null,
      "precision": 5,
      "scale": 2
    }
  ],
  "returnType": "DECIMAL",
  "definition": "CREATE FUNCTION calculate_total(order_id INTEGER, tax_rate DECIMAL(5,2))\nRETURNS DECIMAL(10,2)\nLANGUAGE SQL\nAS $$\n  SELECT subtotal * (1 + tax_rate) FROM orders WHERE id = order_id;\n$$;",
  "language": "sql",
  "isDeterministic": true,
  "remarks": "Calculates order total including tax",
  "links": {
    "parent": "db://connection/mydb/schema/public"
  }
}
```

## Usage Patterns

### 1. Discover Procedures
```
Read: db://connection/{id}/schema/{schema}
→ Get list of procedures with URIs
```

### 2. Get Procedure Details
```
Read: db://connection/{id}/schema/{schema}/procedure/{name}
→ Get complete procedure metadata
```

### 3. Examine Parameters
```json
{
  "parameters": [
    {"name": "param1", "type": "VARCHAR", "mode": "IN", "position": 1},
    {"name": "result", "type": "INTEGER", "mode": "OUT", "position": 2}
  ]
}
```

### 4. View Definition
```json
{
  "definition": "CREATE FUNCTION ... AS $$ ... $$;"
}
```

## Benefits

### For LLMs

1. **Complete Context**: All procedure metadata in one request
2. **Parameter Details**: Know exactly what inputs/outputs a procedure has
3. **Source Code**: Can analyze procedure logic
4. **Type Safety**: Return types and parameter types clearly specified
5. **Determinism Info**: Helps understand procedure behavior

### For Developers

1. **Documentation**: Procedures are self-documenting via resources
2. **Discovery**: Easy to find and understand available procedures
3. **Integration**: Can generate client code from parameter metadata
4. **Debugging**: View actual source code
5. **Analysis**: Understand dependencies and behavior

## Architecture Completion

With ProcedureResource, we now have complete resource coverage:

| Database Object | Resource URI Pattern |
|----------------|---------------------|
| Connection | `db://connection/{id}` |
| Schema | `db://connection/{id}/schema/{schema}` |
| Table | `db://connection/{id}/schema/{schema}/table/{table}` |
| View | `db://connection/{id}/schema/{schema}/view/{view}` |
| Procedure | `db://connection/{id}/schema/{schema}/procedure/{proc}` ✅ NEW |

**Resource Hierarchy:**
```
Context (overview)
  └─ Connections (list)
      └─ Connection (details)
          ├─ Schemas (list)
          │   └─ Schema (details - includes tables/views/procedures)
          │       ├─ Table (structure)
          │       ├─ View (definition)
          │       └─ Procedure (details) ✅ NEW
          └─ Relationships (FK graph)
```

## Comparison with Deleted Tool

### Old: ListProceduresTool
- Returned list of all procedures with full details
- Single tool call for all procedures in schema
- Included definitions, parameters, etc. for ALL procedures
- Could be very large response (many tokens)

### New: SchemaResource + ProcedureResource
- **SchemaResource**: Lists procedure names and types with URIs
- **ProcedureResource**: Details for specific procedure on demand
- LLM can navigate to specific procedures as needed
- Smaller, more focused responses
- Better token efficiency

**Token Comparison (10 procedures):**
- Old: ~5000-8000 tokens (all details upfront)
- New: ~200 tokens (schema) + ~400 per procedure (on demand)
- Savings: Significant if LLM only needs 1-2 procedures

## Testing

Tests verify:
- ✅ Correct URI generation
- ✅ Metadata extraction (name, schema, type)
- ✅ Parameter information retrieval
- ✅ Definition retrieval (database-specific)
- ✅ Navigation links
- ✅ Graceful handling of non-existent procedures

## Known Limitations

1. **Parameter Details**: Some databases provide limited parameter metadata via JDBC
2. **Definition Retrieval**: Database-specific; not all databases expose source
3. **Overloaded Procedures**: JDBC metadata may not distinguish between overloads
4. **System Procedures**: Some databases have many system procedures
5. **Permissions**: Definition retrieval may require specific database privileges

## Future Enhancements

1. **Overload Handling**: Better support for procedure overloading
2. **Dependencies**: Track what procedures call other procedures/functions
3. **Usage Stats**: Execution count, last called, average duration
4. **Security Info**: Execution privileges, security definer vs invoker
5. **Caching**: Cache procedure definitions (they rarely change)

## Related Documentation

- `SCHEMA_PROCEDURES_AND_COUNT_REMOVAL.md` - Procedures added to SchemaResource
- `LIST_PROCEDURES_TOOL_REMOVAL.md` - ListProceduresTool removal
- `JDBC_MCP_QUICK_REFERENCE.md` - Updated with procedure patterns
- `JDBC_MCP_CLEANUP_SUMMARY.md` - Overall architecture cleanup

## Conclusion

The ProcedureResource completes the resource-based metadata access architecture, providing:
- Consistent pattern for all database objects
- On-demand detail retrieval
- Token-efficient navigation
- Complete metadata coverage

The JDBC MCP now provides comprehensive, navigable access to all database metadata through a clean, resource-oriented API.

