# JSQLParser 5.3 CTE DML Detection Fix

**Date:** December 22, 2025

## Problem

After upgrading JSQLParser to version 5.3, six tests in `ReadOnlySqlValidatorTest` were failing:

- `testRejectDeleteInCTE` - CTE with DELETE statement
- `testRejectInsertSelectInCTE` - CTE with INSERT...SELECT statement  
- `testRejectMultipleCTEsWithMixedDML` - Multiple CTEs with different DML operations
- `testRejectMultipleDMLInCTE` - CTE with multiple DML operations
- `testRejectNestedCTEWithDML` - Nested CTE with DML
- `testRejectWithClauseModifyingData` - WITH clause with UPDATE statement

All tests expected an `IllegalArgumentException` to be thrown when validating SQL with DML statements in CTEs, but nothing was thrown.

## Root Cause

The `ReadOnlySqlValidator` was not checking the contents of Common Table Expressions (CTEs) for DML statements (DELETE, INSERT, UPDATE, MERGE, TRUNCATE). It only verified that the top-level statement was a SELECT.

PostgreSQL and other databases support "data-modifying CTEs" where DML statements with RETURNING clauses can be used in WITH clauses:

```sql
WITH deleted_users AS (
    DELETE FROM users WHERE status = 'inactive' RETURNING *
)
SELECT * FROM deleted_users;
```

While the top-level statement is a SELECT, the CTE executes a DELETE, violating the read-only requirement.

## Solution

Added two layers of protection:

### 1. String-Based Pre-Check (Primary Detection)

Added a pre-parsing check in `checkForUnparseableStatements()` that uses string matching to detect DML keywords in CTEs before JSQLParser even attempts to parse the SQL. This approach:

- Runs before parsing, catching issues early
- Handles cases where JSQLParser might not parse DML in CTEs correctly
- Checks for patterns like `WITH name AS (DELETE ...`, `WITH name AS (INSERT ...`, etc.
- Accounts for various whitespace variations (spaces, newlines, tabs)

```java
if (normalized.contains("WITH ") && normalized.contains(" AS (")) {
    String cteSection = normalized.substring(withPos, selectPos);
    
    if (cteSection.contains(" AS (DELETE ") || cteSection.contains(" AS ( DELETE ")) {
        throw new IllegalArgumentException(
            "Only SELECT queries are allowed. CTE with DELETE is not permitted.");
    }
    // ... similar checks for INSERT, UPDATE, MERGE, TRUNCATE
}
```

### 2. AST-Based CTE Inspection (Backup Detection)

Added `checkCTEsForDML()` method that inspects the parsed AST for DML in CTEs:

- Iterates through all `WithItem` objects in the SELECT statement
- Examines the SELECT body of each CTE
- Checks the string representation for DML keywords at the start
- Works with JSQLParser 5.x's generic `WithItem<?>` API

```java
private static List<String> checkCTEsForDML(Select select) {
    List<WithItem<?>> withItems = select.getWithItemsList();
    if (withItems != null && !withItems.isEmpty()) {
        for (WithItem<?> withItem : withItems) {
            Object selectBody = withItem.getSelect();
            if (selectBody != null) {
                String cteUpper = selectBody.toString().trim().toUpperCase();
                if (cteUpper.startsWith("DELETE ")) {
                    violations.add("CTE '" + withItem.getAlias().getName() + 
                                 "' contains DELETE statement");
                }
                // ... similar checks for INSERT, UPDATE, MERGE, TRUNCATE
            }
        }
    }
}
```

## Changes Made

### Modified Files

**`ReadOnlySqlValidator.java`:**

1. Added import for `WithItem` class
2. Added `checkCTEsForDML()` method to inspect parsed CTEs
3. Called `checkCTEsForDML()` from the main validation loop
4. Enhanced `checkForUnparseableStatements()` with string-based CTE DML detection

### API Compatibility

The changes work with JSQLParser 5.3's updated API:
- `WithItem` is now generic (`WithItem<?>`)
- The `getSelect()` method returns `Object` instead of specific types
- Used `toString()` on the select body for string-based analysis

## Testing

Created `CTEDMLTest.java` with focused tests for the three main DML operations in CTEs:
- DELETE in CTE
- INSERT in CTE  
- UPDATE in CTE

These tests verify that `IllegalArgumentException` is thrown with appropriate error messages mentioning the DML operation or CTE.

## Why Two Layers?

1. **String-based check** - Fast, runs before parsing, catches obvious cases
2. **AST-based check** - More accurate for complex cases that parse successfully

This dual approach ensures maximum coverage and handles edge cases where JSQLParser's parsing behavior might vary.

## Future Considerations

If JSQLParser 5.x fully supports parsing DML with RETURNING in CTEs and provides proper AST nodes, we could enhance the AST-based detection to check node types instead of string matching. However, the current string-based approach is robust and handles the upgrade without depending on uncertain parser behavior.

## References

- PostgreSQL Data-Modifying CTEs: https://www.postgresql.org/docs/current/queries-with.html#QUERIES-WITH-MODIFYING
- JSQLParser 5.3 Changes: Generic WithItem, API updates

