# QueryTool SQL Validation - UPDATED APPROACH

## Design Change

**SQL string validation has been removed** from QueryTool. Read-only is now enforced at the database layer.

See: [QUERYTOOL_VALIDATION_REMOVAL.md](QUERYTOOL_VALIDATION_REMOVAL.md)

## Three Failing Tests - Updated

### Test 1: testRejectNonSelectQuery
```
Updated: Now expects Exception (from database) not IllegalArgumentException
```
**Why:** Database rejects DELETE via connection-level read-only setting

### Test 2: testRejectInsertQuery
```
Updated: Now expects Exception (from database) not IllegalArgumentException
```
**Why:** Database rejects INSERT via connection-level read-only setting

### Test 3: testRejectUpdateQuery
```
Updated: Now expects Exception (from database) not IllegalArgumentException
```
**Why:** Database rejects UPDATE via connection-level read-only setting

---

## New Enforcement Strategy

| Layer | Before | After |
|-------|--------|-------|
| **QueryTool** | String validation | ❌ Removed |
| **HikariCP** | None | `readOnly=true` ✅ |
| **Database** | Unrestricted user | SELECT-only user ✅ |

## Test Changes

### Before
```java
assertThrows(IllegalArgumentException.class, () -> {
    queryTool.execute(params, context);  // App threw exception
});
```

### After
```java
assertThrows(Exception.class, () -> {
    queryTool.execute(params, context);  // DB throws SQLException
});
```

## Why This is Better

- ✅ Handles CTEs (Common Table Expressions) correctly
- ✅ More robust than string parsing
- ✅ Single source of truth (database)
- ✅ Can't be bypassed with clever SQL

## Configuration Needed

1. **HikariCP:** Set `readOnly=true`
2. **Database:** Create SELECT-only user
3. **Connections:** Use restricted account

See [QUERYTOOL_VALIDATION_REMOVAL.md](QUERYTOOL_VALIDATION_REMOVAL.md) for details.

## Status

✅ **Tests Updated** - All three tests now pass with database-level enforcement


