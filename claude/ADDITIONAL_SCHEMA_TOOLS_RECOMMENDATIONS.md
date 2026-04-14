# Additional Database Schema Tools for LLM Understanding

**Date:** December 30, 2025

## Current Tools

The current JDBC MCP server provides these tools:

1. **list-connections** - List available database connections
2. **list-schemas** - List schemas/catalogs in the database
3. **list-tables** - List tables and views
4. **describe-table** - Column definitions, PKs, FKs, indexes
5. **preview-table** - Sample data from a table
6. **get-row-count** - Count rows in a table
7. **query** - Execute read-only SQL queries

## Recommended Additional Tools

### 1. **list-views** (with definitions)

**Purpose:** List views WITH their SQL definitions

**Why needed:**
- Views encapsulate business logic
- Understanding view definitions helps LLMs generate correct queries
- Views may join multiple tables - LLM needs to know this

**Schema:**
```json
{
  "view": "order_summary",
  "schema": "public",
  "definition": "SELECT o.id, c.name, SUM(oi.amount) FROM orders o JOIN customers c ON o.customer_id = c.id JOIN order_items oi ON o.id = oi.order_id GROUP BY o.id, c.name",
  "columns": ["id", "name", "total"],
  "isUpdatable": false,
  "checkOption": "CASCADED"
}
```

**Use Case:**
```
User: "Generate code to get customer order totals"
LLM: *Sees order_summary view exists*
LLM: "You already have a view for this - just SELECT * FROM order_summary"
```

---

### 2. **list-stored-procedures** / **describe-procedure**

**Purpose:** List and describe stored procedures and functions

**Why needed:**
- Business logic often lives in stored procedures
- LLM should suggest using existing procedures instead of reimplementing
- Parameters and return types needed for correct usage

**Schema:**
```json
{
  "name": "calculate_order_total",
  "schema": "public",
  "type": "FUNCTION",
  "parameters": [
    {"name": "order_id", "type": "INTEGER", "mode": "IN"}
  ],
  "returnType": "DECIMAL(10,2)",
  "definition": "SELECT SUM(quantity * price) FROM order_items WHERE order_id = $1",
  "language": "SQL",
  "isDeterministic": true
}
```

**Use Case:**
```
User: "Calculate the total for order #123"
LLM: *Sees calculate_order_total function exists*
LLM: "Use: SELECT calculate_order_total(123)"
```

---

### 3. **list-triggers**

**Purpose:** List triggers on tables

**Why needed:**
- Triggers affect INSERT/UPDATE/DELETE behavior
- LLM needs to know side effects of data changes
- Critical for understanding audit trails, cascading updates

**Schema:**
```json
{
  "name": "audit_customer_changes",
  "table": "customers",
  "schema": "public",
  "timing": "AFTER",
  "events": ["UPDATE", "DELETE"],
  "orientation": "ROW",
  "definition": "INSERT INTO audit_log(...) VALUES (...)"
}
```

**Use Case:**
```
User: "Delete customer #42"
LLM: *Sees audit trigger exists*
LLM: "Note: Deleting this customer will create an audit log entry automatically"
```

---

### 4. **list-check-constraints**

**Purpose:** List CHECK constraints on tables

**Why needed:**
- Validation rules in database affect valid data
- LLM can generate correct validation code
- Prevents suggesting invalid data patterns

**Schema:**
```json
{
  "name": "chk_positive_quantity",
  "table": "order_items",
  "column": "quantity",
  "definition": "quantity > 0",
  "isEnforced": true
}
```

**Use Case:**
```
User: "Generate code to insert an order item"
LLM: *Sees CHECK constraint: quantity > 0*
LLM: "Validate quantity > 0 before inserting"
```

---

### 5. **list-unique-constraints**

**Purpose:** List UNIQUE constraints (separate from PKs)

**Why needed:**
- Unique constraints affect data integrity
- Natural keys for lookups
- LLM can suggest appropriate WHERE clauses

**Schema:**
```json
{
  "name": "uq_customer_email",
  "table": "customers",
  "columns": ["email"],
  "isDeferrable": false
}
```

**Use Case:**
```
User: "Look up a customer"
LLM: *Sees email is unique*
LLM: "You can find a customer by email (it's unique): WHERE email = ?"
```

---

### 6. **list-sequences**

**Purpose:** List sequences in the database

**Why needed:**
- Sequences are used for ID generation
- LLM needs to know if IDs are auto-generated
- Important for INSERT statements

**Schema:**
```json
{
  "name": "orders_id_seq",
  "schema": "public",
  "dataType": "BIGINT",
  "startValue": 1,
  "increment": 1,
  "currentValue": 12345,
  "ownedByTable": "orders",
  "ownedByColumn": "id"
}
```

**Use Case:**
```
User: "Insert a new order"
LLM: *Sees id column uses sequence*
LLM: "Don't specify ID - it's auto-generated from sequence"
```

---

### 7. **get-table-statistics**

**Purpose:** Get statistics about table data distribution

**Why needed:**
- Helps LLM suggest efficient queries
- Understand data cardinality
- Identify potential performance issues

**Schema:**
```json
{
  "table": "orders",
  "rowEstimate": 1000000,
  "diskSize": "125 MB",
  "indexSize": "45 MB",
  "columnStats": [
    {
      "column": "status",
      "distinctValues": 5,
      "nullFraction": 0.0,
      "mostCommonValues": ["completed", "pending", "shipped"],
      "mostCommonFrequencies": [0.7, 0.15, 0.1]
    }
  ]
}
```

**Use Case:**
```
User: "Find all pending orders"
LLM: *Sees status has 5 values, 'pending' is 15%*
LLM: "This will return ~150,000 rows - consider adding pagination"
```

---

### 8. **list-table-dependencies**

**Purpose:** Show dependency graph between tables

**Why needed:**
- Understand table relationships holistically
- Plan complex queries involving multiple tables
- Understand cascade effects

**Schema:**
```json
{
  "table": "orders",
  "dependsOn": [
    {"table": "customers", "via": "customer_id", "type": "foreign_key"},
    {"table": "products", "via": "order_items", "type": "junction"}
  ],
  "referencedBy": [
    {"table": "order_items", "via": "order_id", "type": "foreign_key"},
    {"table": "payments", "via": "order_id", "type": "foreign_key"}
  ]
}
```

**Use Case:**
```
User: "Show me everything about order #123"
LLM: *Sees orders has order_items, payments as children*
LLM: "I'll join orders with order_items and payments to show complete info"
```

---

### 9. **get-enum-values**

**Purpose:** List values for ENUM types or constrained columns

**Why needed:**
- Understand valid values for columns
- Generate correct code with proper options
- Validate user input

**Schema:**
```json
{
  "name": "order_status",
  "schema": "public",
  "values": ["pending", "processing", "shipped", "delivered", "cancelled"],
  "usedByColumns": [
    {"table": "orders", "column": "status"}
  ]
}
```

**Use Case:**
```
User: "Update order status"
LLM: *Sees valid status values*
LLM: "Valid statuses are: pending, processing, shipped, delivered, cancelled"
```

---

### 10. **describe-column-extended**

**Purpose:** Get extended column metadata including computed columns, defaults, etc.

**Why needed:**
- Computed columns shouldn't be in INSERT
- Default values may handle optional fields
- Generated columns are read-only

**Schema:**
```json
{
  "table": "orders",
  "column": "total",
  "type": "DECIMAL(10,2)",
  "isComputed": true,
  "computeExpression": "subtotal + tax - discount",
  "isStored": true,
  "generation": "STORED"
}
```

**Use Case:**
```
User: "Insert a new order with total"
LLM: *Sees total is computed*
LLM: "Don't specify total - it's automatically calculated from subtotal + tax - discount"
```

---

### 11. **list-partitions**

**Purpose:** Show table partitioning information

**Why needed:**
- Partitioned tables have special query patterns
- Partition keys should be in WHERE clauses for performance
- Understand data organization

**Schema:**
```json
{
  "table": "events",
  "partitionType": "RANGE",
  "partitionKey": ["created_at"],
  "partitions": [
    {"name": "events_2024", "range": ["2024-01-01", "2025-01-01"]},
    {"name": "events_2025", "range": ["2025-01-01", "2026-01-01"]}
  ]
}
```

**Use Case:**
```
User: "Query events from last month"
LLM: *Sees table is partitioned by created_at*
LLM: "Include date range in WHERE clause for partition pruning"
```

---

### 12. **explain-query**

**Purpose:** Get query execution plan

**Why needed:**
- Understand query performance
- Identify missing indexes
- Optimize generated queries

**Schema:**
```json
{
  "sql": "SELECT * FROM orders WHERE customer_id = 123",
  "plan": {
    "operation": "Index Scan",
    "index": "idx_orders_customer",
    "estimatedRows": 15,
    "estimatedCost": 8.5,
    "warnings": []
  }
}
```

**Use Case:**
```
User: "Is this query efficient?"
LLM: *Runs EXPLAIN*
LLM: "Yes, it uses idx_orders_customer index, estimated 15 rows"
```

---

### 13. **get-database-info**

**Purpose:** Get database metadata (version, settings, capabilities)

**Why needed:**
- SQL syntax varies by database version
- Features availability (JSON support, CTEs, window functions)
- Generate compatible code

**Schema:**
```json
{
  "productName": "PostgreSQL",
  "productVersion": "15.4",
  "driverName": "PostgreSQL JDBC Driver",
  "driverVersion": "42.6.0",
  "features": {
    "supportsTransactions": true,
    "supportsSavepoints": true,
    "supportsStoredProcedures": true,
    "supportsFullOuterJoins": true,
    "maxTableNameLength": 63,
    "identifierQuoteString": "\"",
    "sqlKeywords": ["LIMIT", "OFFSET", "RETURNING"]
  }
}
```

**Use Case:**
```
User: "Generate pagination query"
LLM: *Sees PostgreSQL with LIMIT/OFFSET support*
LLM: "SELECT * FROM orders LIMIT 10 OFFSET 20"
```

---

## Priority Ranking

### High Priority (Most Useful for Code Generation)

1. **list-stored-procedures / describe-procedure** - Reuse existing business logic
2. **list-check-constraints** - Validation rules for data
3. **list-unique-constraints** - Alternative lookup keys
4. **get-enum-values** - Valid values for constrained columns
5. **list-views** - Understand encapsulated queries

### Medium Priority (Helpful for Complex Scenarios)

6. **list-triggers** - Understand side effects
7. **list-sequences** - ID generation strategy
8. **describe-column-extended** - Computed/generated columns
9. **list-table-dependencies** - Relationship graph
10. **get-database-info** - Compatibility info

### Lower Priority (Performance/Advanced)

11. **get-table-statistics** - Query optimization
12. **explain-query** - Performance analysis
13. **list-partitions** - Large table optimization

---

## Implementation Considerations

### Database Compatibility

Not all databases support all metadata:

| Tool | PostgreSQL | MySQL | Oracle | SQL Server | SQLite |
|------|------------|-------|--------|------------|--------|
| list-views | ✅ | ✅ | ✅ | ✅ | ✅ |
| list-stored-procedures | ✅ | ✅ | ✅ | ✅ | ❌ |
| list-triggers | ✅ | ✅ | ✅ | ✅ | ✅ |
| list-check-constraints | ✅ | ✅ (8.0+) | ✅ | ✅ | ✅ |
| list-unique-constraints | ✅ | ✅ | ✅ | ✅ | ✅ |
| list-sequences | ✅ | ✅ (8.0+) | ✅ | ✅ | ❌ |
| get-table-statistics | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| list-partitions | ✅ | ✅ | ✅ | ✅ | ❌ |
| explain-query | ✅ | ✅ | ✅ | ✅ | ✅ |
| get-enum-values | ✅ | ✅ | ⚠️ | ⚠️ | ❌ |

### JDBC Metadata Methods

Many of these can be implemented with standard JDBC:

```java
// Procedures
metaData.getProcedures(catalog, schemaPattern, procedureNamePattern)
metaData.getProcedureColumns(...)

// Views (via getTables with type filter)
metaData.getTables(catalog, schema, pattern, new String[]{"VIEW"})

// Indexes (includes unique constraints)
metaData.getIndexInfo(catalog, schema, table, unique, approximate)

// Database info
metaData.getDatabaseProductName()
metaData.getDatabaseProductVersion()
metaData.supportsTransactions()
// etc.
```

Database-specific queries needed for:
- View definitions (SELECT definition FROM information_schema.views)
- Trigger details
- Check constraints
- Partition information
- Statistics

---

## Example Workflow

### User: "Generate code to create a new order"

**LLM's Tool Calls:**

1. `describe-table("orders")` → columns, PKs, FKs
2. `list-sequences()` → sees orders_id_seq
3. `list-check-constraints("orders")` → status must be in enum
4. `get-enum-values("order_status")` → valid status values
5. `describe-column-extended("orders", "total")` → sees it's computed
6. `list-triggers("orders")` → sees audit trigger

**LLM's Response:**
```java
// Order ID is auto-generated from sequence
// Don't include 'total' - it's computed from subtotal + tax - discount
// Status must be one of: pending, processing, shipped, delivered, cancelled
// Note: An audit trigger will log this insert

PreparedStatement stmt = conn.prepareStatement(
    "INSERT INTO orders (customer_id, status, subtotal, tax, discount) " +
    "VALUES (?, 'pending', ?, ?, ?) RETURNING id"
);
stmt.setInt(1, customerId);
stmt.setBigDecimal(2, subtotal);
stmt.setBigDecimal(3, tax);
stmt.setBigDecimal(4, discount);
```

---

## Summary

The current tools provide a good foundation, but these additions would significantly improve LLM understanding:

**Must Have:**
- ✅ Stored procedures/functions (reuse logic)
- ✅ Check constraints (validation rules)
- ✅ Unique constraints (lookup keys)
- ✅ Enum values (valid options)

**Should Have:**
- ✅ View definitions (understand encapsulated queries)
- ✅ Triggers (side effects)
- ✅ Sequences (ID generation)
- ✅ Database info (compatibility)

**Nice to Have:**
- ✅ Table dependencies (relationship graph)
- ✅ Statistics (performance hints)
- ✅ Partitions (large table handling)
- ✅ Query explain (optimization)

---

*"To understand a database, you must understand not just its structure, but its behavior, constraints, and the intentions encoded within it."*

With these tools, an LLM would have comprehensive database understanding to generate correct, efficient, and idiomatic code!

