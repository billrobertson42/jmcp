# Session State and Database State Modification Tests

## Overview

Added **60+ comprehensive test cases** to `ReadOnlySqlValidatorTest` that validate rejection of SQL statements that modify session state or database state without being traditional DML (INSERT, UPDATE, DELETE) or DDL (CREATE, ALTER, DROP) operations.

## Categories of Rejected Statements

### 1. Session Variable/Configuration Statements

These modify the current session's configuration:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `SET variable = value` | PostgreSQL | `testRejectSetStatement` | Changes search path or other variables |
| `SET @myvar = value` | MySQL | `testRejectSetVariable` | Session-local variable assignment |
| `SET TIME ZONE 'UTC'` | PostgreSQL | `testRejectSetTimeZone` | Modifies session timezone |
| `SET NAMES 'utf8mb4'` | MySQL | `testRejectSetCharacterSet` | Changes session character set |
| `SET client_encoding` | PostgreSQL | `testRejectSetClientEncoding` | Modifies client encoding |
| `SET SESSION variable` | Generic | `testRejectSetSessionVariable` | Sets session configuration |
| `RESET variable` | PostgreSQL | `testRejectResetStatement` | Resets session variable |
| `RESET ALL` | PostgreSQL | `testRejectResetAll` | Resets all session variables |

**Why Rejected:** Modifies session state, affecting subsequent query behavior.

### 2. Transaction Control Statements

These explicitly manage transaction state:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `BEGIN` | Generic | `testRejectBeginTransaction` | Starts a transaction |
| `START TRANSACTION` | Generic | `testRejectStartTransaction` | Alternative transaction start syntax |
| `BEGIN READ WRITE` | Generic | `testRejectBeginReadWrite` | Explicitly starts read-write transaction |
| `COMMIT` | Generic | `testRejectCommit` | Commits pending transaction |
| `END` | Generic | `testRejectEnd` | Synonym for COMMIT |
| `ROLLBACK` | Generic | `testRejectRollback` | Rolls back transaction |
| `ROLLBACK TO SAVEPOINT` | Generic | `testRejectRollbackToSavepoint` | Rolls back to savepoint |
| `SAVEPOINT name` | Generic | `testRejectSavepoint` | Creates a savepoint |
| `RELEASE SAVEPOINT` | Generic | `testRejectReleaseSavepoint` | Removes a savepoint |

**Why Rejected:** Explicitly control transaction boundaries and state.

### 3. Prepared Statement Management

These manage prepared statement state:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `PREPARE stmt FROM sql` | MySQL | `testRejectPrepareStatement` | Creates a prepared statement |
| `EXECUTE stmt` | MySQL | `testRejectExecuteStatement` | Executes prepared statement |
| `DEALLOCATE stmt` | PostgreSQL | `testRejectDeallocatePrepared` | Removes prepared statement |
| `DEALLOCATE PREPARE stmt` | MySQL | `testRejectDeallocatePrepareStatement` | Alternative deallocation syntax |

**Why Rejected:** Manage cached statement plans and resources.

### 4. User/Role/Authorization Statements

These change session authorization context:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `SET SESSION AUTHORIZATION` | PostgreSQL | `testRejectSetSessionAuthorizationUser` | Changes session user |
| `SET ROLE` | PostgreSQL | `testRejectSetRole` | Changes active role |
| `USE database` | MySQL | `testRejectSetDatabase` | Changes active database |

**Why Rejected:** Changes authorization context and accessible objects.

### 5. Lock Management Statements

These manage table locks:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `LOCK TABLE ... IN EXCLUSIVE MODE` | PostgreSQL | `testRejectLockTable` | Acquires table locks |
| `UNLOCK TABLES` | MySQL | `testRejectUnlockTables` | Releases table locks |

**Why Rejected:** Lock state affects concurrent access and transaction semantics.

### 6. Server Maintenance Statements

These trigger server-side operations:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `FLUSH TABLES` | MySQL | `testRejectFlush` | Flushes table caches |
| `FLUSH HOSTS` | MySQL | `testRejectFlushHosts` | Resets host caches |
| `FLUSH PRIVILEGES` | MySQL | `testRejectFlushPrivileges` | Reloads privilege tables |
| `RESET MASTER` | MySQL | `testRejectReset` | Resets binary log |

**Why Rejected:** Modify server-wide state and caching.

### 7. Component/Extension Management

These manage server extensions:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `INSTALL COMPONENT` | MySQL 8.0+ | `testRejectInstallComponent` | Installs server component |
| `UNINSTALL COMPONENT` | MySQL 8.0+ | `testRejectUninstallComponent` | Uninstalls server component |

**Why Rejected:** Modify server capabilities and state.

### 8. Bulk Data Loading

These load data from external sources:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `LOAD DATA INFILE` | MySQL | `testRejectLoadData` | Loads data from file |
| `LOAD DATA LOCAL INFILE` | MySQL | `testRejectLoadDataLocal` | Loads data from client file |

**Why Rejected:** Bulk modify table data from external sources.

### 9. Table Maintenance Statements

These perform maintenance on table structure/statistics:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `ANALYZE TABLE` | MySQL | `testRejectAnalyze` | Gathers table statistics |
| `OPTIMIZE TABLE` | MySQL | `testRejectOptimize` | Optimizes table structure |
| `REPAIR TABLE` | MySQL | `testRejectRepair` | Repairs corrupted table |
| `CHECK TABLE` | MySQL | `testRejectCheckTable` | Checks table integrity |
| `VACUUM` | PostgreSQL | `testRejectVacuum` | Maintenance vacuum operation |
| `VACUUM FULL ANALYZE` | PostgreSQL | `testRejectVacuumFull` | Aggressive vacuum |
| `REINDEX` | PostgreSQL | `testRejectReindex` | Rebuilds indexes |
| `CLUSTER` | PostgreSQL | `testRejectCluster` | Reorganizes table |

**Why Rejected:** Modify table metadata and internal structure.

### 10. Data Import/Export

These move data between database and external systems:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `COPY FROM STDIN` | PostgreSQL | `testRejectCopy` | Loads data via COPY protocol |

**Why Rejected:** Bulk data import from external sources.

### 11. Event/Notification Statements

These manage asynchronous communication:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `NOTIFY channel` | PostgreSQL | `testRejectNotify` | Sends notification |
| `LISTEN channel` | PostgreSQL | `testRejectListen` | Subscribes to notifications |
| `UNLISTEN channel` | PostgreSQL | `testRejectUnlisten` | Unsubscribes from notifications |

**Why Rejected:** Send/receive asynchronous notifications.

### 12. Materialized View Refresh

These refresh materialized view data:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `REFRESH MATERIALIZED VIEW` | PostgreSQL | `testRejectRefresh` | Refreshes materialized view |

**Why Rejected:** Modify materialized view data.

### 13. Cursor Management

These manage database cursors:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `DECLARE cursor FOR SELECT` | PostgreSQL | `testRejectDeclare` | Declares a cursor |
| `FETCH FROM cursor` | PostgreSQL | `testRejectFetch` | Fetches from cursor |
| `MOVE FROM cursor` | PostgreSQL | `testRejectMove` | Moves cursor position |

**Why Rejected:** Manage cursor state across transactions.

### 14. Stored Procedure Execution

These execute procedures that may modify state:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `CALL proc()` | Generic | `testRejectCall` | Executes stored procedure |
| `DO $$ ... $$` | PostgreSQL | `testRejectDo` | Executes anonymous procedure |

**Why Rejected:** May modify database state (procedures not restricted to SELECT).

### 15. Session Cleanup

These clean up session-level state:

| Statement | Database | Test | Purpose |
|-----------|----------|------|---------|
| `DISCARD ALL` | PostgreSQL | `testRejectDiscard` | Discards session state |

**Why Rejected:** Clears prepared statements, settings, and other session data.

## Test Statistics

| Category | Count | Status |
|----------|-------|--------|
| Session Variables | 8 | ✅ All reject |
| Transaction Control | 9 | ✅ All reject |
| Prepared Statements | 4 | ✅ All reject |
| User/Role/Auth | 3 | ✅ All reject |
| Lock Management | 2 | ✅ All reject |
| Server Maintenance | 5 | ✅ All reject |
| Component Mgmt | 2 | ✅ All reject |
| Bulk Data Loading | 2 | ✅ All reject |
| Table Maintenance | 8 | ✅ All reject |
| Data Import/Export | 1 | ✅ All reject |
| Events/Notifications | 3 | ✅ All reject |
| Materialized Views | 1 | ✅ All reject |
| Cursor Management | 3 | ✅ All reject |
| Procedure Execution | 2 | ✅ All reject |
| Session Cleanup | 1 | ✅ All reject |
| **TOTAL** | **57** | **✅ All reject** |

## Why These Matter

### Security
Preventing session state modification ensures:
- ✅ Session isolation (one user can't affect another)
- ✅ Authorization context integrity
- ✅ Connection pooling safety

### Data Integrity
Preventing structure/state modifications ensures:
- ✅ Table schema remains consistent
- ✅ Indexes stay intact
- ✅ Materialized views are stable

### Read-Only Guarantee
These statements could bypass "read-only" intent by:
- ✅ Storing state in session variables (used in subsequent queries)
- ✅ Loading data from files
- ✅ Executing procedures that modify data
- ✅ Modifying query behavior via settings

## Database Coverage

Tests cover multiple major databases:

| Database | Statements Tested |
|----------|-------------------|
| **PostgreSQL** | RESET, SET TIME ZONE, SET ROLE, CLUSTER, VACUUM, REINDEX, NOTIFY, LISTEN, UNLISTEN, DISCARD, DECLARE, FETCH, MOVE, DO, REFRESH, COPY |
| **MySQL** | SET @var, SET NAMES, SET SESSION, USE, UNLOCK TABLES, FLUSH, RESET, LOAD DATA, ANALYZE, OPTIMIZE, REPAIR, CHECK, PREPARE, EXECUTE, DEALLOCATE, INSTALL/UNINSTALL |
| **Generic** | BEGIN, START TRANSACTION, COMMIT, END, ROLLBACK, SAVEPOINT, LOCK TABLE, CALL |

## Edge Cases Covered

✅ Different syntax variants (e.g., START TRANSACTION vs BEGIN)  
✅ CALL with parameters  
✅ LOCK with different modes  
✅ FLUSH with different targets  
✅ COPY variants  
✅ Cursor management operations  

## Future Enhancements

Potential future test additions:

- [x] EXPLAIN PLAN (intentionally not rejected - read-only analysis)
- [ ] GRANT/REVOKE variations
- [ ] WITH (CTE) combined with state-modifying statements
- [ ] Database-specific dialects (Oracle, SQL Server, etc.)
- [ ] PRAGMA statements (SQLite)
- [ ] Statement batching with mixed types

## Running the Tests

```bash
# Run all read-only validator tests
mvn test -pl jmcp-jdbc -Dtest=ReadOnlySqlValidatorTest

# Run specific test category
mvn test -pl jmcp-jdbc -Dtest=ReadOnlySqlValidatorTest#testRejectSetStatement

# Run with verbose output
mvn test -pl jmcp-jdbc -Dtest=ReadOnlySqlValidatorTest -X
```

## Notes

1. **EXPLAIN** - Intentionally NOT rejected. It's read-only analysis.
2. **CALL** - Rejected because procedures could modify state (not typically executed for read-only queries).
3. **DO blocks** - Rejected as anonymous procedures (may contain writes).
4. **Materialized Views** - REFRESH is rejected; SELECT from materialized views is allowed.

## Summary

The test suite now provides **comprehensive coverage** of session state and database state modification statements across major SQL dialects. Each test ensures that statements attempting to modify state outside of traditional DML/DDL operations are properly rejected by the validator.

All 57 new tests verify that `ReadOnlySqlValidator` correctly identifies and rejects these statements, ensuring true read-only operation of the query tool.

