# Test Fix: JdbcResourcesTest Function Alias Cleanup

**Date:** January 11, 2026

## Issue

When running multiple tests in JdbcResourcesTest, the second test would fail with:

```
org.h2.jdbc.JdbcSQLSyntaxErrorException: Function alias "CALCULATE_TAX" already exists
```

The error occurred because:
1. The in-memory H2 database is reused across tests
2. The setup() method tried to create the function alias without checking if it already existed
3. The tearDown() method did not drop the function alias
4. On subsequent tests, the alias remained from the previous test

## Root Cause

The setup and teardown methods were incomplete:

**Setup before fix:**
- Created tables, views, and alias
- No DROP before CREATE for the alias

**Teardown before fix:**
- Dropped view and tables
- **Did NOT drop the alias** ← This was the problem

## Solution

Made two simple changes to JdbcResourcesTest:

### 1. In setUp() - Add DROP before CREATE

```java
// Drop the alias if it exists from previous test run
stmt.execute("DROP ALIAS IF EXISTS test_schema.calculate_tax");
// Now safe to create it
stmt.execute("CREATE ALIAS test_schema.calculate_tax AS $$...");
```

### 2. In tearDown() - Drop the alias first

```java
stmt.execute("DROP ALIAS IF EXISTS test_schema.calculate_tax");  // ← Added this line
stmt.execute("DROP VIEW IF EXISTS test_schema.user_orders");
stmt.execute("DROP TABLE IF EXISTS test_schema.orders");
stmt.execute("DROP TABLE IF EXISTS test_schema.users");
stmt.execute("DROP SCHEMA IF EXISTS test_schema");
```

## Why This Works

1. **Idempotent Creation**: `DROP ALIAS IF EXISTS` is safe - succeeds whether the alias exists or not
2. **Clean Teardown**: Alias is dropped before dependent objects, preventing cascade errors
3. **Test Isolation**: Each test starts with a clean state
4. **Order Matters**: Drop the alias before the schema it belongs to

## Dependency Order

H2 (and most databases) have dependencies:
```
Schema
  ├─ Table (depends on schema)
  ├─ View (depends on table)
  └─ Alias (depends on schema)
```

So drop order must be:
1. **Alias** (least dependent)
2. **View** (depends on table)
3. **Tables** (depend on schema)
4. **Schema** (highest level)

## Testing

The fix ensures:
- ✅ First test runs successfully
- ✅ Alias is created in setUp
- ✅ Alias is properly dropped in tearDown
- ✅ Second test setUp can create a fresh alias
- ✅ All subsequent tests run without conflicts

## Related Files

- JdbcResourcesTest.java - Contains the test fix
- ProcedureResource.java - Feature that prompted the test addition
- ProcedureResourceTest.java - Standalone test for procedure resource

## Lesson Learned

When using in-memory databases with reused connections across tests:
- Always use `IF EXISTS`/`IF NOT EXISTS` clauses
- Drop objects in reverse dependency order
- Ensure tearDown matches setUp in completeness

