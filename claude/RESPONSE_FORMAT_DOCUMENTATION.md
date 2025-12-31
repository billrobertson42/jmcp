# JDBC MCP Tool Response Formats

**Date:** December 29, 2025

## Overview

All JDBC MCP tools that return tabular data use a **compact array-based format** to minimize token usage. This provides 40-60% token savings compared to traditional object-based formats.

## Format Philosophy

1. **Token Efficiency** - Column names listed once, not repeated per row
2. **LLM Friendly** - Arrays are easy for LLMs to parse and understand
3. **Type Preservation** - Values maintain their JSON types (numbers, strings, nulls, booleans)
4. **Order Preservation** - Column order in `cols` array matches row value order

---

## Tool Response Formats

### 1. query (QueryTool)

**Description:** Execute read-only SQL SELECT queries

**Response Format:**
```json
{
  "cols": ["column1", "column2", "column3"],
  "rows": [
    [value1, value2, value3],
    [value1, value2, value3],
    ...
  ],
  "count": 10,
  "more": false
}
```

**Fields:**
- `cols` (array of strings) - Column names in order
- `rows` (array of arrays) - Data rows, each row is an array of values matching column order
- `count` (integer) - Number of rows returned
- `more` (boolean) - True if more rows available beyond MAX_ROWS limit. **Omitted if false** (using `@JsonInclude(NON_DEFAULT)`)

**Example:**
```json
{
  "cols": ["ORDER_ID", "CUSTOMER_NAME", "TOTAL", "ORDER_DATE"],
  "rows": [
    [1, "Alice", 100.00, "2025-01-01"],
    [2, "Bob", 200.00, "2025-01-02"],
    [3, "Charlie", 150.50, "2025-01-03"]
  ],
  "count": 3
}
```

**Note:** If `more` is false, it's omitted from the response to save tokens.

---

### 2. preview-table (PreviewTableTool)

**Description:** Preview first N rows of a table (default 10, max 100)

**Response Format:**
```json
{
  "table": "table_name",
  "schema": "schema_name",
  "cols": ["column1", "column2", "column3"],
  "rows": [
    [value1, value2, value3],
    [value1, value2, value3],
    ...
  ],
  "count": 10
}
```

**Fields:**
- `table` (string) - Table name
- `schema` (string) - Schema name. **Omitted if null** (using `@JsonInclude(NON_NULL)`)
- `cols` (array of strings) - Column names in order
- `rows` (array of arrays) - Data rows
- `count` (integer) - Number of rows returned

**Example:**
```json
{
  "table": "products",
  "schema": "public",
  "cols": ["ID", "NAME", "PRICE", "CATEGORY"],
  "rows": [
    [1, "Widget", 19.99, "Tools"],
    [2, "Gadget", 29.99, "Electronics"],
    [3, "Doohickey", 39.99, "Misc"]
  ],
  "count": 3
}
```

**Omitted Fields:**
- `limit` - Redundant (LLM knows the requested limit)
- `schema` - Only included if not null

---

### 3. describe-table (DescribeTableTool)

**Description:** Get detailed table structure (columns, types, constraints)

**Response Format:**
```json
{
  "table": "table_name",
  "schema": "schema_name",
  "columns": [
    {
      "name": "column_name",
      "type": "DATA_TYPE",
      "size": 100,
      "nullable": true,
      "defaultValue": "default",
      "remarks": "comment"
    },
    ...
  ],
  "primaryKeys": ["col1", "col2"],
  "indexes": [
    {
      "name": "idx_name",
      "column": "col_name",
      "unique": true
    },
    ...
  ]
}
```

**Note:** This tool still uses object format for columns because:
- Not tabular data (metadata, not rows)
- Small response size
- Rich structure with many optional fields

**Fields use `@JsonInclude(NON_NULL)` to omit:**
- `size` - If null
- `nullable` - If null
- `defaultValue` - If null
- `remarks` - If null or empty
- `schema` - If null

---

### 4. get-row-count (GetRowCountTool)

**Description:** Get count of rows in a table

**Response Format:**
```json
{
  "table": "table_name",
  "schema": "schema_name",
  "rowCount": 12345
}
```

**Fields:**
- `table` (string) - Table name
- `schema` (string) - Schema name. **Omitted if null**
- `rowCount` (long) - Number of rows in table

**Example:**
```json
{
  "table": "orders",
  "schema": "public",
  "rowCount": 6789
}
```

---

### 5. list-tables (ListTablesTool)

**Description:** List all tables in database or schema

**Response Format:**
```json
{
  "tables": [
    {
      "schema": "public",
      "name": "table1",
      "type": "TABLE",
      "remarks": "comment"
    },
    ...
  ],
  "count": 10
}
```

**Fields:**
- `tables` (array of objects) - Table information
- `count` (integer) - Number of tables

**Table Object Fields:**
- `schema` (string) - Schema name. **Omitted if null**
- `name` (string) - Table name
- `type` (string) - "TABLE" or "VIEW"
- `remarks` (string) - Table comment. **Omitted if null or empty**

**Example:**
```json
{
  "tables": [
    {"schema": "public", "name": "orders", "type": "TABLE"},
    {"schema": "public", "name": "customers", "type": "TABLE"},
    {"schema": "public", "name": "order_summary", "type": "VIEW"}
  ],
  "count": 3
}
```

---

### 6. list-schemas (ListSchemasTool)

**Description:** List all schemas/catalogs in database

**Response Format:**
```json
{
  "schemas": [
    {
      "name": "schema_name",
      "catalog": "catalog_name"
    },
    ...
  ],
  "count": 5
}
```

**Fields:**
- `schemas` (array of objects) - Schema information
- `count` (integer) - Number of schemas

**Schema Object Fields:**
- `name` (string) - Schema name
- `catalog` (string) - Catalog name. **Omitted if null**

**Example:**
```json
{
  "schemas": [
    {"name": "public"},
    {"name": "reporting"},
    {"name": "staging"}
  ],
  "count": 3
}
```

---

### 7. list-connections (ListConnectionsTool)

**Description:** List available database connections

**Response Format:**
```json
{
  "connections": [
    {
      "id": "connection_id",
      "url": "jdbc:postgresql://localhost/db",
      "username": "user",
      "databaseType": "postgresql"
    },
    ...
  ],
  "defaultId": "primary_db"
}
```

**Fields:**
- `connections` (array of objects) - Connection information
- `defaultId` (string) - ID of default connection. **Omitted if null**

**Connection Object Fields:**
- `id` (string) - Connection identifier
- `url` (string) - JDBC URL (sensitive parts masked with ****)
- `username` (string) - Database username
- `databaseType` (string) - Database type (postgresql, mysql, etc.)

**Example:**
```json
{
  "connections": [
    {
      "id": "prod_db",
      "url": "jdbc:postgresql://localhost/production?password=****",
      "username": "app_user",
      "databaseType": "postgresql"
    },
    {
      "id": "dev_db",
      "url": "jdbc:h2:mem:testdb",
      "username": "sa",
      "databaseType": "h2"
    }
  ],
  "defaultId": "prod_db"
}
```

---

## Understanding Array-Based Row Format

### Column-to-Value Mapping

The `cols` array defines the column order, and each row array contains values in the same order:

```json
{
  "cols": ["ID", "NAME", "PRICE"],
  "rows": [
    [1, "Widget", 19.99],
    [2, "Gadget", 29.99]
  ]
}
```

**How to read:**
- Row 0: ID=1, NAME="Widget", PRICE=19.99
- Row 1: ID=2, NAME="Gadget", PRICE=29.99

### Accessing Values

**By position:**
```javascript
// First row, second column (NAME)
let name = response.rows[0][1];  // "Widget"
```

**By column name:**
```javascript
// Find column index, then access value
let nameIndex = response.cols.indexOf("NAME");
let name = response.rows[0][nameIndex];  // "Widget"
```

### NULL Values

NULL database values are represented as JSON `null`:

```json
{
  "cols": ["ID", "NAME", "OPTIONAL_FIELD"],
  "rows": [
    [1, "Widget", null],
    [2, "Gadget", "value"]
  ]
}
```

---

## Token Savings Comparison

### Example: 3 rows, 4 columns

**Verbose Object Format (OLD):**
```json
{
  "columns": [
    {"name": "ID", "type": "INTEGER"},
    {"name": "NAME", "type": "VARCHAR"},
    {"name": "PRICE", "type": "DECIMAL"},
    {"name": "STOCK", "type": "INTEGER"}
  ],
  "rows": [
    {"ID": 1, "NAME": "Widget", "PRICE": 19.99, "STOCK": 100},
    {"ID": 2, "NAME": "Gadget", "PRICE": 29.99, "STOCK": 50},
    {"ID": 3, "NAME": "Thing", "PRICE": 39.99, "STOCK": 25}
  ],
  "rowCount": 3,
  "hasMore": false
}
```
**Tokens:** ~220

**Compact Array Format (NEW):**
```json
{
  "cols": ["ID", "NAME", "PRICE", "STOCK"],
  "rows": [
    [1, "Widget", 19.99, 100],
    [2, "Gadget", 29.99, 50],
    [3, "Thing", 39.99, 25]
  ],
  "count": 3
}
```
**Tokens:** ~90

**Savings: 59% (130 tokens saved)**

### Scaling

| Rows | Columns | Verbose | Compact | Savings |
|------|---------|---------|---------|---------|
| 10 | 5 | ~600 | ~240 | 60% |
| 50 | 6 | ~1,500 | ~600 | 60% |
| 100 | 10 | ~5,000 | ~2,000 | 60% |

**Key insight:** Savings scale with result size - larger results = more savings!

---

## LLM Usage Examples

### Example 1: Querying Data

**LLM Request:**
```json
{
  "sql": "SELECT customer_id, name, total_orders FROM customers WHERE total_orders > 10 ORDER BY total_orders DESC LIMIT 5"
}
```

**Response:**
```json
{
  "cols": ["CUSTOMER_ID", "NAME", "TOTAL_ORDERS"],
  "rows": [
    [42, "Acme Corp", 156],
    [17, "Widget Inc", 89],
    [93, "Gadget Co", 67],
    [28, "Tech LLC", 45],
    [55, "Solutions Ltd", 34]
  ],
  "count": 5
}
```

**LLM can easily:**
- Identify top customer: "Acme Corp with 156 orders"
- Compare values: "Acme Corp has almost 2x orders of Widget Inc"
- Access specific values: rows[0][2] = 156

### Example 2: Table Preview

**LLM Request:**
```json
{
  "table": "products",
  "limit": 3
}
```

**Response:**
```json
{
  "table": "products",
  "schema": "public",
  "cols": ["ID", "NAME", "PRICE", "CATEGORY"],
  "rows": [
    [1, "Laptop", 999.99, "Electronics"],
    [2, "Mouse", 29.99, "Accessories"],
    [3, "Keyboard", 79.99, "Accessories"]
  ],
  "count": 3
}
```

**LLM can understand:**
- Column structure from `cols`
- Data types from values
- Row-by-row data easily

---

## Best Practices for LLMs

### 1. Column Order Matters
Always use the `cols` array to map column positions to names.

### 2. Handle NULLs
Check for `null` values in arrays:
```javascript
if (rows[i][colIndex] === null) {
  // Handle NULL
}
```

### 3. Type Inference
JSON types are preserved:
- Numbers: `19.99`, `42`
- Strings: `"Widget"`
- Booleans: `true`, `false`
- Nulls: `null`

### 4. Check 'more' Flag
If `more: true` is present, there are additional rows beyond the limit.

### 5. Empty Results
Empty result sets return:
```json
{
  "cols": ["COL1", "COL2"],
  "rows": [],
  "count": 0
}
```

---

## JSON Schema Documentation

### CompactQueryResult Schema

```typescript
interface CompactQueryResult {
  cols: string[];           // Column names in order
  rows: any[][];           // Array of row arrays
  count: number;           // Number of rows returned
  more?: boolean;          // True if more rows available (omitted if false)
}
```

### CompactTablePreviewResult Schema

```typescript
interface CompactTablePreviewResult {
  table: string;           // Table name
  schema?: string;         // Schema name (omitted if null)
  cols: string[];          // Column names in order
  rows: any[][];          // Array of row arrays
  count: number;          // Number of rows returned
}
```

---

## Migration Notes

### From Verbose to Compact

**Old Code (expecting objects):**
```javascript
rows.forEach(row => {
  console.log(row.NAME, row.PRICE);
});
```

**New Code (using arrays):**
```javascript
const nameIdx = cols.indexOf("NAME");
const priceIdx = cols.indexOf("PRICE");

rows.forEach(row => {
  console.log(row[nameIdx], row[priceIdx]);
});
```

**Or iterate by column:**
```javascript
rows.forEach(row => {
  cols.forEach((col, idx) => {
    console.log(`${col}: ${row[idx]}`);
  });
});
```

---

*"The best notation is no notation."* - Adapted from Edward Tufte

For MCP responses: The most efficient format is arrays - minimal overhead, maximum clarity!

