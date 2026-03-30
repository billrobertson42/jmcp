# MCP Usage Analysis - February 16, 2026

## Overview

Analysis of `mcp_usage_summary.md` (real usage on February 15, 2026) compared to jmcp-jdbc's actual capabilities.

## Key Finding: User Missed Existing Features

The user ran complex `information_schema` queries for foreign key discovery, but **the TableResource already provides this information**:

| User's Query | Existing Resource |
|--------------|-------------------|
| `information_schema.table_constraints` JOIN for FKs | `TableResource.foreignKeys` |
| Find tables that reference a given table | `TableResource.reverseForeignKeys` |
| Complete FK relationship graph | `RelationshipsResource` |
| List all tables in schema | `SchemaResource.tables` |

### Root Cause: Documentation Insufficiency

The `ContextResource` descriptions were too brief. Users didn't understand that:
- `foreignKeys` = tables THIS table references
- `reverseForeignKeys` = tables that REFERENCE this table

## Changes Made

### 1. Improved Runtime Descriptions (ContextResource)

**Tool descriptions now include:**
- Query tool: Mentions using SELECT COUNT(*) for filtered counts
- Get-row-count: Clarifies it's for total counts, not filtered
- Sample-data: Added "preview actual data values"
- Analyze-column: Changed to "Data profiling"

**Resource descriptions now include:**
- TableResource: "foreignKeys (tables this references), reverseForeignKeys (tables that reference this)"
- RelationshipsResource: "with topological copy order for dependency-safe data operations"
- SchemaRelationshipsResource: "with copyOrder for dependency-safe operations"

**Usage hints now include:**
- "For FK discovery: use table resource (foreignKeys + reverseForeignKeys)"
- "For copy order: use relationships resource (topologically sorted)"
- "For filtered counts: use query tool with SELECT COUNT(*) ... WHERE"

### 2. Added Topological Sort to Relationships Resources

Both `RelationshipsResource` and `SchemaRelationshipsResource` now include:
- `copyOrder`: List of tables in dependency order (copy first to last)
- `cyclesDetected`: List of tables involved in circular dependencies (null if none)

Algorithm: Kahn's algorithm for topological sort, handles circular dependencies gracefully.

### 3. Updated TableResource Description

Changed from: "Table structure: columns, primary keys, indexes, foreign keys (both directions)."
Changed to: "Columns, PKs, indexes, foreignKeys (tables this references), reverseForeignKeys (tables that reference this)."

## No Changes Needed (Already Implemented)

- Foreign key discovery (both directions) via TableResource ✓
- Schema/table listing via SchemaResource ✓
- Sample data tool ✓
- Query tool with validation ✓
- Analyze column tool ✓

## Database-Agnostic Compliance

All implementations use standard JDBC DatabaseMetaData APIs:
- `getImportedKeys()` - for foreignKeys
- `getExportedKeys()` - for reverseForeignKeys
- `getSchemas()` - for schema listing
- `getTables()` - for table listing

No database-specific features used.


