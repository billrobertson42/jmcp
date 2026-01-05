# Runtime Documentation Consistency Review

**Date:** January 4, 2026

## Overview

Reviewed and updated all runtime documentation (tool and resource descriptions) to be concise, consistent, and token-efficient for LLM consumption.

---

## Changes Made

### Tools (4 total)

#### 1. query
**Before:** "Execute a read-only SQL SELECT query. Returns up to 1000 rows. Returns compact format: {\"table\":\"name\", \"schema\":\"name\", \"cols\":[\"col_name1\",\"col_name2\",...], \"rows\":[[val1,val2,...],[...]], \"count\":N}. Data in 'rows' as arrays where the order in each row matches the order of the columns."

**After:** "Execute read-only SELECT query. Returns up to 1000 rows in compact array format."

**Parameters:**
- `sql` - "SELECT query to execute"
- `parameters` - "Query parameters for prepared statement"
- `database_id` - "Database connection ID (optional, uses default)"

---

#### 2. get-row-count
**Before:** "Get the number of rows in a table"

**After:** "Get exact row count for a table."

**Parameters:**
- `table` - "Table name"
- `schema` - "Schema name (optional, uses default)"
- `database_id` - "Database connection ID (optional, uses default)"

---

#### 3. sample-data
**Before:** "Get a sample of data from a table to preview actual values. Supports different sampling strategies: 'first' (first N rows), 'random' (random sample), 'last' (last N rows based on primary key). Returns data in compact array format."

**After:** "Get sample rows from a table. Strategies: 'first', 'random', 'last'. Max 100 rows."

**Parameters:**
- `table` - "Table name"
- `schema` - "Schema name (optional, uses default)"
- `sample_size` - "Rows to sample (default: 10, max: 100)"
- `strategy` - "Sampling strategy: 'first', 'random', 'last' (default: first)"
- `columns` - "Comma-separated column names (optional, default: all)"
- `database_id` - "Database connection ID (optional, uses default)"

---

#### 4. analyze-column
**Before:** "Analyze a column to understand its data distribution: distinct value count, null count, min/max values, and most common values with frequencies. Use for data profiling and quality assessment."

**After:** "Analyze column data: distinct count, nulls, min/max, top values with frequencies."

**Parameters:**
- `table` - "Table name"
- `column` - "Column name to analyze"
- `schema` - "Schema name (optional, uses default)"
- `top_values` - "Most common values to return (default: 10, max: 50)"
- `database_id` - "Database connection ID (optional, uses default)"

---

### Resources (8 total)

#### 1. db://context (ContextResource)
**Before:** "Comprehensive summary of all database connections, schemas, and tables. Use this resource to understand what databases are available and how to query them."

**After:** "Complete overview: all connections, schemas, tables, tools, and resources."

---

#### 2. db://connections (ConnectionsListResource)
**Before:** "List of available database connections. Navigate to individual connections to explore schemas and tables."

**After:** "List of all configured database connections with navigation links."

---

#### 3. db://connection/{id} (ConnectionResource)
**Before:** "Database connection details with navigation to schemas, tables, and views."

**After:** "Connection metadata with navigation to schemas and relationships."

---

#### 4. db://connection/{id}/schemas (SchemasListResource)
**Before:** "List of database schemas/catalogs. Navigate to individual schemas for table listings."

**After:** "List of schemas with navigation links and metadata."

---

#### 5. db://connection/{id}/schema/{schema} (SchemaResource)
**Before:** "Schema details with navigation to tables, views, and other database objects."

**After:** "Schema with lists of tables and views, including navigation URIs."

---

#### 6. db://connection/{id}/schema/{schema}/table/{table} (TableResource)
**Before:** "Table structure details including columns, primary keys, indexes, foreign keys (outgoing references), and reverse foreign keys (incoming references)."

**After:** "Table structure: columns, primary keys, indexes, foreign keys (both directions)."

---

#### 7. db://connection/{id}/schema/{schema}/view/{view} (ViewResource)
**Before:** "View structure details including columns and SQL definition."

**After:** "View structure: columns and SQL definition."

---

#### 8. db://connection/{id}/relationships (RelationshipsResource)
**Before:** "Complete foreign key relationship graph across all schemas in the database."

**After:** "Complete foreign key relationship graph for all schemas."

---

#### 9. db://connection/{id}/schema/{schema}/relationships (SchemaRelationshipsResource)
**Before:** "Foreign key relationships involving tables in the {schema} schema (includes relationships to/from other schemas)."

**After:** "Foreign key relationships involving this schema (including cross-schema FKs)."

---

### ContextResource Updates

#### Tool Listing
**Removed:** Non-existent "get-table-statistics" tool reference

**Updated:** All 4 tools listed with concise descriptions matching actual tool descriptions

#### Usage Hints
**Before (10 hints):**
```
"Start with db://context to see available connections and resources"
"Use resources (not tools) to explore database structure - they're cacheable and provide navigation"
"Resources provide: connections list, schemas, tables, views, relationships, and complete metadata"
"Tools are for operations: query execution, row counts, statistics, data sampling, and analysis"
"Navigate resources via URIs: db://connections → db://connection/{id}/schemas → db://connection/{id}/schema/{name}"
"Table structure and relationships: db://connection/{id}/schema/{schema}/table/{table}"
"The query tool only allows SELECT statements for safety"
"If schema is not specified in tools, the database's default schema is used"
"Use sample-data tool to preview actual data values"
"Use analyze-column tool for data profiling and quality assessment"
```

**After (6 hints):**
```
"Start with db://context for complete overview"
"Use resources for structure (cacheable metadata)"
"Use tools for operations (query, count, sample, analyze)"
"Navigate: db://connections → db://connection/{id} → schema/{schema} → table/{table}"
"Query tool accepts SELECT only"
"Schema defaults to connection's default if not specified"
```

**Token reduction:** ~60% fewer tokens

#### Resource Navigation Hints
**Before (5 hints):**
```
"Resources are cacheable - they represent database metadata that changes infrequently"
"Use db://context (this resource) for a complete overview without navigation"
"For specific details, navigate the resource hierarchy or use tools"
"Table resources include both imported FKs (this table references X) and exported FKs (X references this table)"
"The relationships resource provides the complete FK graph across all schemas"
```

**After (3 hints):**
```
"Resources are cacheable metadata"
"Table resources include FKs in both directions"
"Relationships resource provides complete FK graph"
```

**Token reduction:** ~70% fewer tokens

---

## Principles Applied

### 1. Conciseness
- Removed verbose explanations
- Eliminated redundant words ("optional", "if not provided", etc.)
- Shortened compound sentences

### 2. Consistency
- Tool parameter descriptions follow same pattern
- Resource descriptions use same structure
- All "optional" parameters marked consistently

### 3. Token Efficiency
- Removed implementation details
- Removed motivational language
- Removed examples from descriptions (save for docs)

### 4. Completeness
- All required information present
- Parameter names, types, defaults included
- Core functionality clearly stated

### 5. No Implementation Details
- Don't explain WHY things work
- Don't explain HOW things are implemented
- Just state WHAT they do

---

## Token Savings Estimate

### Tool Descriptions
- Query: ~130 tokens → ~18 tokens (86% reduction)
- Get-row-count: ~12 tokens → ~8 tokens (33% reduction)
- Sample-data: ~60 tokens → ~18 tokens (70% reduction)
- Analyze-column: ~50 tokens → ~15 tokens (70% reduction)

**Total tool descriptions:** ~252 tokens → ~59 tokens (**77% reduction**)

### Resource Descriptions
- Average: ~20 tokens → ~10 tokens per resource

**Total resource descriptions:** ~160 tokens → ~80 tokens (**50% reduction**)

### ContextResource Hints
- Usage hints: ~200 tokens → ~80 tokens (60% reduction)
- Navigation hints: ~120 tokens → ~40 tokens (67% reduction)

**Total hints:** ~320 tokens → ~120 tokens (**62% reduction**)

### Grand Total
**Overall token reduction:** ~730 tokens → ~260 tokens (**~65% reduction**)

---

## Consistency Checks

### ✅ Tool Names
All tools in ContextResource match actual registered tools:
- query ✓
- get-row-count ✓
- sample-data ✓
- analyze-column ✓

### ✅ Parameter Descriptions
All parameter descriptions follow pattern:
- "{noun}" or "{noun} ({qualification})"
- No "The", "Optional", or verbose phrasing
- Defaults and limits in parentheses when relevant

### ✅ Resource URIs
All resource URI templates in ContextResource match actual resource routing:
- db://connections ✓
- db://connection/{database_id} ✓
- db://connection/{database_id}/relationships ✓
- db://connection/{database_id}/schemas ✓
- db://connection/{database_id}/schema/{schema_name} ✓
- db://connection/{database_id}/schema/{schema_name}/relationships ✓
- db://connection/{database_id}/schema/{schema_name}/table/{table_name} ✓
- db://connection/{database_id}/schema/{schema_name}/view/{view_name} ✓

### ✅ Description Tone
All descriptions:
- State facts only
- No implementation details
- No motivational language
- No examples

---

## Files Modified

### Tools
1. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/tools/QueryTool.java`
2. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/tools/GetRowCountTool.java`
3. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/tools/SampleDataTool.java`
4. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/tools/AnalyzeColumnTool.java`

### Resources
5. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/ContextResource.java`
6. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/ConnectionsListResource.java`
7. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/ConnectionResource.java`
8. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/SchemasListResource.java`
9. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/SchemaResource.java`
10. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/TableResource.java`
11. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/ViewResource.java`
12. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/RelationshipsResource.java`
13. `/jmcp-jdbc/src/main/java/org/peacetalk/jmcp/jdbc/resources/SchemaRelationshipsResource.java`

**Total:** 13 files modified

---

## Verification

✅ **Compilation:** All files compile without errors  
✅ **Consistency:** Tool/resource names match actual implementations  
✅ **Completeness:** All required information present  
✅ **Conciseness:** Significant token reduction achieved  
✅ **Clarity:** Descriptions remain clear and informative

---

## Impact

### For LLMs
- **Faster processing** - Less text to parse
- **Lower costs** - Fewer tokens consumed
- **Clearer intent** - No distracting details
- **Better caching** - Metadata fits in context better

### For Users
- **Quicker understanding** - Less to read
- **Clearer options** - Focus on what matters
- **Consistent experience** - Same patterns everywhere

### For Developers
- **Easier maintenance** - Less verbose documentation to update
- **Clearer boundaries** - Documentation doesn't mix with implementation
- **Better separation** - Runtime docs separate from detailed docs

---

## Conclusion

Successfully reviewed and updated all runtime documentation to be:
- ✅ **Concise** - 65% token reduction overall
- ✅ **Consistent** - Same patterns across all tools/resources
- ✅ **Complete** - All essential information retained
- ✅ **Correct** - All references match actual implementations

The documentation now serves its purpose efficiently: providing LLMs with just enough information to use the tools and resources correctly, without implementation details or motivational language.

