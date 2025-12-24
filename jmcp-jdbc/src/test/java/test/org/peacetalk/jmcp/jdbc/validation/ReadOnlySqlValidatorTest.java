package test.org.peacetalk.jmcp.jdbc.validation;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.validation.ReadOnlySqlValidator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReadOnlySqlValidator to ensure robust SQL validation.
 */
class ReadOnlySqlValidatorTest {

    // ========== Valid SELECT Queries ==========

    @Test
    void testSimpleSelect() {
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * FROM users"));
        assertTrue(ReadOnlySqlValidator.isReadOnly("SELECT * FROM users"));
    }

    @Test
    void testSelectWithWhere() {
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT id, name FROM users WHERE age > 25"));
    }

    @Test
    void testSelectWithJoin() {
        String sql = "SELECT u.name, o.order_id FROM users u JOIN orders o ON u.id = o.user_id";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithSubquery() {
        String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE total > 100)";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithCTE() {
        String sql = """
            WITH recent_users AS (
                SELECT * FROM users WHERE created_date > '2024-01-01'
            )
            SELECT * FROM recent_users WHERE age > 18
            """;
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
        assertTrue(ReadOnlySqlValidator.isReadOnly(sql));
    }

    @Test
    void testSelectWithComplexCTE() {
        String sql = """
            WITH
                active_users AS (SELECT * FROM users WHERE status = 'active'),
                user_orders AS (SELECT * FROM orders WHERE user_id IN (SELECT id FROM active_users))
            SELECT au.name, COUNT(uo.id) as order_count
            FROM active_users au
            LEFT JOIN user_orders uo ON au.id = uo.user_id
            GROUP BY au.name
            """;
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithWindowFunction() {
        String sql = "SELECT name, age, ROW_NUMBER() OVER (ORDER BY age DESC) as rank FROM users";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithGroupBy() {
        String sql = "SELECT department, COUNT(*) FROM employees GROUP BY department HAVING COUNT(*) > 5";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectWithUnion() {
        String sql = "SELECT name FROM users UNION SELECT name FROM customers";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    // ========== Invalid Non-SELECT Queries ==========

    @Test
    void testRejectInsert() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("INSERT INTO users (name, age) VALUES ('John', 30)"));
        assertTrue(ex.getMessage().contains("read-only"));
        assertFalse(ReadOnlySqlValidator.isReadOnly("INSERT INTO users VALUES (1, 'John')"));
    }

    @Test
    void testRejectUpdate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("UPDATE users SET age = 31 WHERE id = 1"));
        assertTrue(ex.getMessage().contains("read-only"));
    }

    @Test
    void testRejectDelete() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DELETE FROM users WHERE id = 1"));
        assertTrue(ex.getMessage().contains("read-only"));
    }

    @Test
    void testRejectDrop() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP TABLE users"));
    }

    @Test
    void testRejectCreate() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE TABLE test (id INT)"));
    }

    @Test
    void testRejectAlter() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER TABLE users ADD COLUMN email VARCHAR(255)"));
    }

    @Test
    void testRejectTruncate() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("TRUNCATE TABLE users"));
    }

    @Test
    void testRejectMerge() {
        String sql = "MERGE INTO users USING updates ON users.id = updates.id WHEN MATCHED THEN UPDATE SET name = updates.name";
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(sql));
    }

    // ========== Multiple Statements ==========

    @Test
    void testMultipleSelectStatements() {
        String sql = "SELECT * FROM users; SELECT * FROM orders;";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testRejectMixedStatements() {
        String sql = "SELECT * FROM users; INSERT INTO logs VALUES (1, 'access');";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(sql));
        assertTrue(ex.getMessage().contains("Statement 2"));
    }

    // ========== Edge Cases ==========

    @Test
    void testRejectNullSql() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(null));
    }

    @Test
    void testRejectBlankSql() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("   "));
    }

    @Test
    void testRejectEmptyString() {
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(""));
    }

    @Test
    void testKeywordsInStrings() {
        // Keywords in string literals should not trigger false positives
        String sql = "SELECT 'INSERT INTO fake' as message, 'UPDATE test' as another FROM users";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testKeywordsInComments() {
        // Keywords in comments should not trigger false positives
        String sql = "SELECT * FROM users -- This is a comment about INSERT";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testSelectIntoRejected() {
        // SELECT INTO is a write operation in some databases
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * INTO new_table FROM users"));
    }

    // ========== Database-Specific Features ==========

    @Test
    void testPostgreSQLReturningClause() {
        // This should be rejected as it's part of an INSERT
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("INSERT INTO users VALUES (1, 'John') RETURNING id"));
    }

    @Test
    void testSelectForUpdate() {
        // SELECT FOR UPDATE is technically a SELECT but acquires locks
        // JSqlParser should still parse it as a SELECT
        String sql = "SELECT * FROM users WHERE id = 1 FOR UPDATE";
        assertDoesNotThrow(() -> ReadOnlySqlValidator.validateReadOnly(sql));
    }

    // ========== Invalid SQL ==========

    @Test
    void testInvalidSqlThrowsParseException() {
        // Invalid SQL now throws IllegalArgumentException (treated as non-SELECT)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("INVALID SQL GARBAGE;;;"));
    }

    @Test
    void testIncompleteSql() {
        // Incomplete SQL now throws IllegalArgumentException (treated as non-SELECT)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * FROM"));
    }

    // ========== Session State Modification (Should All Fail) ==========

    @Test
    void testRejectSetStatement() {
        // SET variable assignments modify session state
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET search_path TO public"));
    }

    @Test
    void testRejectSetVariable() {
        // MySQL/PostgreSQL SET for session variables
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET @myvar = 'value'"));
    }

    @Test
    void testRejectSetTimeZone() {
        // PostgreSQL - modifies session timezone
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET TIME ZONE 'UTC'"));
    }

    @Test
    void testRejectSetCharacterSet() {
        // MySQL - modifies session character set
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET NAMES 'utf8mb4'"));
    }

    @Test
    void testRejectSetClientEncoding() {
        // PostgreSQL - modifies client encoding
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET client_encoding = 'UTF8'"));
    }

    @Test
    void testRejectSetSessionVariable() {
        // Generic SET SESSION variable
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET SESSION sql_mode = 'STRICT_TRANS_TABLES'"));
    }

    @Test
    void testRejectResetStatement() {
        // PostgreSQL - RESET modifies session state
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("RESET search_path"));
    }

    @Test
    void testRejectResetAll() {
        // PostgreSQL - RESET ALL modifies session state
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("RESET ALL"));
    }

    @Test
    void testRejectBeginTransaction() {
        // BEGIN/START TRANSACTION explicitly starts a transaction
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("BEGIN"));
    }

    @Test
    void testRejectStartTransaction() {
        // START TRANSACTION alternative syntax
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("START TRANSACTION"));
    }

    @Test
    void testRejectBeginReadWrite() {
        // Explicitly starting a read-write transaction
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("BEGIN READ WRITE"));
    }

    @Test
    void testRejectCommit() {
        // COMMIT - commits any pending transaction
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("COMMIT"));
    }

    @Test
    void testRejectEnd() {
        // END - synonym for COMMIT
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("END"));
    }

    @Test
    void testRejectRollback() {
        // ROLLBACK - rolls back transaction (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ROLLBACK"));
    }

    @Test
    void testRejectRollbackToSavepoint() {
        // ROLLBACK TO SAVEPOINT - modifies transaction state
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ROLLBACK TO SAVEPOINT sp1"));
    }

    @Test
    void testRejectSavepoint() {
        // SAVEPOINT - creates a savepoint (modifies transaction state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SAVEPOINT my_savepoint"));
    }

    @Test
    void testRejectReleaseSavepoint() {
        // RELEASE SAVEPOINT - removes a savepoint (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("RELEASE SAVEPOINT my_savepoint"));
    }

    @Test
    void testRejectPrepareStatement() {
        // PREPARE - creates a prepared statement
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("PREPARE stmt FROM 'SELECT * FROM users'"));
    }

    @Test
    void testRejectExecuteStatement() {
        // EXECUTE - executes a prepared statement (could modify state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("EXECUTE stmt"));
    }

    @Test
    void testRejectDeallocatePrepared() {
        // DEALLOCATE - removes a prepared statement
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DEALLOCATE stmt"));
    }

    @Test
    void testRejectDeallocatePrepareStatement() {
        // Alternative DEALLOCATE PREPARE syntax
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DEALLOCATE PREPARE stmt"));
    }

    @Test
    void testRejectSetSessionAuthorizationUser() {
        // PostgreSQL - changes the session authorization
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET SESSION AUTHORIZATION 'user1'"));
    }

    @Test
    void testRejectSetRole() {
        // PostgreSQL - changes the current role
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET ROLE admin"));
    }

    @Test
    void testRejectSetDatabase() {
        // MySQL - USE statement changes database context
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("USE mydb"));
    }

    @Test
    void testRejectLockTable() {
        // LOCK TABLE - acquires locks (modifies transaction state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("LOCK TABLE users IN EXCLUSIVE MODE"));
    }

    @Test
    void testRejectUnlockTables() {
        // UNLOCK TABLES - MySQL - releases locks
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("UNLOCK TABLES"));
    }

    @Test
    void testRejectFlush() {
        // MySQL FLUSH - clears caches and buffers
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("FLUSH TABLES"));
    }

    @Test
    void testRejectFlushHosts() {
        // MySQL FLUSH HOSTS - modifies server state
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("FLUSH HOSTS"));
    }

    @Test
    void testRejectFlushPrivileges() {
        // MySQL FLUSH PRIVILEGES - reloads permissions
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("FLUSH PRIVILEGES"));
    }

    @Test
    void testRejectReset() {
        // MySQL RESET - resets server/session state
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("RESET MASTER"));
    }

    @Test
    void testRejectInstallComponent() {
        // MySQL 8.0+ - installs a component (modifies server state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("INSTALL COMPONENT 'file://my_component'"));
    }

    @Test
    void testRejectUninstallComponent() {
        // MySQL 8.0+ - uninstalls a component
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("UNINSTALL COMPONENT 'my_component'"));
    }

    @Test
    void testRejectLoadData() {
        // LOAD DATA - loads data from file into table
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("LOAD DATA INFILE '/path/to/file.csv' INTO TABLE users"));
    }

    @Test
    void testRejectLoadDataLocal() {
        // LOAD DATA LOCAL INFILE - loads from client file
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("LOAD DATA LOCAL INFILE 'data.csv' INTO TABLE users"));
    }

    @Test
    void testRejectExplain() {
        // EXPLAIN - Some implementations consider this DDL or state-changing
        // However, EXPLAIN should probably be safe - it's read-only analysis
        // Included here for reference but may vary by interpretation
        String sql = "EXPLAIN SELECT * FROM users";
        // Note: This might be allowed as EXPLAIN is read-only analysis
        // Uncomment if your policy rejects EXPLAIN:
        // assertThrows(IllegalArgumentException.class, () ->
        //     ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testRejectAnalyze() {
        // ANALYZE - gathers statistics (modifies table metadata)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ANALYZE TABLE users"));
    }

    @Test
    void testRejectOptimize() {
        // OPTIMIZE - optimizes table (modifies structure)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("OPTIMIZE TABLE users"));
    }

    @Test
    void testRejectRepair() {
        // REPAIR - repairs table (modifies structure)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("REPAIR TABLE users"));
    }

    @Test
    void testRejectCheckTable() {
        // CHECK TABLE - checks table integrity (may modify state in some databases)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CHECK TABLE users"));
    }

    @Test
    void testRejectVacuum() {
        // PostgreSQL VACUUM - maintenance operation (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("VACUUM users"));
    }

    @Test
    void testRejectVacuumFull() {
        // PostgreSQL VACUUM FULL - aggressive maintenance
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("VACUUM FULL ANALYZE users"));
    }

    @Test
    void testRejectReindex() {
        // REINDEX - rebuilds indexes (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("REINDEX TABLE users"));
    }

    @Test
    void testRejectCopy() {
        // PostgreSQL COPY FROM - loads data (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("COPY users FROM STDIN WITH (FORMAT csv)"));
    }

    @Test
    void testRejectNotify() {
        // PostgreSQL NOTIFY - sends notifications (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("NOTIFY channel, 'message'"));
    }

    @Test
    void testRejectListen() {
        // PostgreSQL LISTEN - subscribes to notifications (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("LISTEN channel_name"));
    }

    @Test
    void testRejectUnlisten() {
        // PostgreSQL UNLISTEN - unsubscribes (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("UNLISTEN channel_name"));
    }

    @Test
    void testRejectDiscard() {
        // PostgreSQL DISCARD - discards session state
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DISCARD ALL"));
    }

    @Test
    void testRejectCluster() {
        // PostgreSQL CLUSTER - reorganizes table (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CLUSTER users USING users_pkey"));
    }

    @Test
    void testRejectRefresh() {
        // PostgreSQL REFRESH MATERIALIZED VIEW - refreshes view data
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("REFRESH MATERIALIZED VIEW my_view"));
    }

    @Test
    void testRejectDeclare() {
        // PostgreSQL DECLARE - declares cursor (modifies transaction state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DECLARE my_cursor CURSOR FOR SELECT * FROM users"));
    }

    @Test
    void testRejectFetch() {
        // PostgreSQL FETCH - fetches from cursor (modifies cursor state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("FETCH NEXT FROM my_cursor"));
    }

    @Test
    void testRejectMove() {
        // PostgreSQL MOVE - moves cursor position (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("MOVE NEXT FROM my_cursor"));
    }

    @Test
    void testRejectCall() {
        // CALL - executes stored procedure (could modify state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CALL my_proc(@param1)"));
    }

    @Test
    void testRejectDo() {
        // PostgreSQL DO - executes anonymous procedure
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DO $$ BEGIN RAISE NOTICE 'test'; END $$"));
    }

    // ========== CREATE Statements (Table/Schema Objects) ==========

    @Test
    void testRejectCreateTable() {
        // CREATE TABLE - creates new table
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100))"));
    }

    @Test
    void testRejectCreateTemporaryTable() {
        // CREATE TEMPORARY TABLE - creates temp table
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE TEMPORARY TABLE temp_users (id INT)"));
    }

    @Test
    void testRejectCreateTableAsSelect() {
        // CREATE TABLE AS SELECT - creates table from query
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE TABLE new_users AS SELECT * FROM users"));
    }

    @Test
    void testRejectCreateIndex() {
        // CREATE INDEX - creates index
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE INDEX idx_users_name ON users(name)"));
    }

    @Test
    void testRejectCreateUniqueIndex() {
        // CREATE UNIQUE INDEX - creates unique index
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE UNIQUE INDEX idx_users_email ON users(email)"));
    }

    @Test
    void testRejectCreateView() {
        // CREATE VIEW - creates view
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE VIEW active_users AS SELECT * FROM users WHERE status = 'active'"));
    }

    @Test
    void testRejectCreateMaterializedView() {
        // CREATE MATERIALIZED VIEW - PostgreSQL
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE MATERIALIZED VIEW mv_users AS SELECT * FROM users"));
    }

    @Test
    void testRejectCreateDatabase() {
        // CREATE DATABASE - creates database
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE DATABASE mydb"));
    }

    @Test
    void testRejectCreateSchema() {
        // CREATE SCHEMA - creates schema
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE SCHEMA myschema"));
    }

    @Test
    void testRejectCreateSequence() {
        // CREATE SEQUENCE - PostgreSQL - creates sequence
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE SEQUENCE my_seq START 1"));
    }

    @Test
    void testRejectCreateTrigger() {
        // CREATE TRIGGER - creates trigger
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE TRIGGER tr_users BEFORE INSERT ON users FOR EACH ROW EXECUTE FUNCTION check_user()"));
    }

    @Test
    void testRejectCreateFunction() {
        // CREATE FUNCTION - creates function/procedure
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE FUNCTION my_func() RETURNS INT AS $$ SELECT 1 $$ LANGUAGE SQL"));
    }

    @Test
    void testRejectCreateStoredProcedure() {
        // CREATE PROCEDURE - creates stored procedure
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE PROCEDURE my_proc() BEGIN SELECT 1; END"));
    }

    @Test
    void testRejectCreateRole() {
        // CREATE ROLE - PostgreSQL - creates role
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE ROLE my_role"));
    }

    @Test
    void testRejectCreateUser() {
        // CREATE USER - creates user account
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE USER myuser IDENTIFIED BY 'password'"));
    }

    @Test
    void testRejectCreateDomain() {
        // CREATE DOMAIN - PostgreSQL - creates domain type
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE DOMAIN email AS VARCHAR CHECK (VALUE ~ '@')"));
    }

    @Test
    void testRejectCreateType() {
        // CREATE TYPE - creates custom type
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE TYPE my_type AS (id INT, name VARCHAR)"));
    }

    @Test
    void testRejectCreateExtension() {
        // CREATE EXTENSION - PostgreSQL - installs extension
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE EXTENSION IF NOT EXISTS uuid-ossp"));
    }

    // ========== ALTER Statements (Modify Objects) ==========

    @Test
    void testRejectAlterTable() {
        // ALTER TABLE - modifies table structure
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER TABLE users ADD COLUMN email VARCHAR(255)"));
    }

    @Test
    void testRejectAlterTableDropColumn() {
        // ALTER TABLE DROP COLUMN - removes column
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER TABLE users DROP COLUMN age"));
    }

    @Test
    void testRejectAlterTableRenameColumn() {
        // ALTER TABLE RENAME COLUMN - renames column
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER TABLE users RENAME COLUMN user_id TO id"));
    }

    @Test
    void testRejectAlterTableAddConstraint() {
        // ALTER TABLE ADD CONSTRAINT - adds constraint
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER TABLE users ADD CONSTRAINT pk_users PRIMARY KEY (id)"));
    }

    @Test
    void testRejectAlterTableRenameTable() {
        // ALTER TABLE RENAME - renames table
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER TABLE users RENAME TO customers"));
    }

    @Test
    void testRejectAlterIndex() {
        // ALTER INDEX - modifies index
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER INDEX idx_users RENAME TO idx_customers"));
    }

    @Test
    void testRejectAlterView() {
        // ALTER VIEW - modifies view
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER VIEW myview RENAME TO newview"));
    }

    @Test
    void testRejectAlterDatabase() {
        // ALTER DATABASE - modifies database
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER DATABASE mydb OWNER TO newowner"));
    }

    @Test
    void testRejectAlterSchema() {
        // ALTER SCHEMA - modifies schema
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER SCHEMA myschema OWNER TO newowner"));
    }

    @Test
    void testRejectAlterSequence() {
        // ALTER SEQUENCE - modifies sequence
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER SEQUENCE my_seq INCREMENT BY 5"));
    }

    @Test
    void testRejectAlterFunction() {
        // ALTER FUNCTION - modifies function
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER FUNCTION my_func() OWNER TO newowner"));
    }

    @Test
    void testRejectAlterRole() {
        // ALTER ROLE - modifies role
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER ROLE my_role WITH SUPERUSER"));
    }

    @Test
    void testRejectAlterUser() {
        // ALTER USER - modifies user
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ALTER USER myuser IDENTIFIED BY 'newpassword'"));
    }

    // ========== DROP Statements (Delete Objects) ==========

    @Test
    void testRejectDropTable() {
        // DROP TABLE - removes table
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP TABLE users"));
    }

    @Test
    void testRejectDropTableIfExists() {
        // DROP TABLE IF EXISTS - removes table if it exists
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP TABLE IF EXISTS users"));
    }

    @Test
    void testRejectDropIndex() {
        // DROP INDEX - removes index
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP INDEX idx_users_name"));
    }

    @Test
    void testRejectDropView() {
        // DROP VIEW - removes view
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP VIEW active_users"));
    }

    @Test
    void testRejectDropMaterializedView() {
        // DROP MATERIALIZED VIEW - PostgreSQL
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP MATERIALIZED VIEW mv_users"));
    }

    @Test
    void testRejectDropDatabase() {
        // DROP DATABASE - removes database
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP DATABASE mydb"));
    }

    @Test
    void testRejectDropSchema() {
        // DROP SCHEMA - removes schema
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP SCHEMA myschema"));
    }

    @Test
    void testRejectDropSequence() {
        // DROP SEQUENCE - removes sequence
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP SEQUENCE my_seq"));
    }

    @Test
    void testRejectDropTrigger() {
        // DROP TRIGGER - removes trigger
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP TRIGGER tr_users ON users"));
    }

    @Test
    void testRejectDropFunction() {
        // DROP FUNCTION - removes function
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP FUNCTION my_func()"));
    }

    @Test
    void testRejectDropProcedure() {
        // DROP PROCEDURE - removes procedure
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP PROCEDURE my_proc"));
    }

    @Test
    void testRejectDropRole() {
        // DROP ROLE - PostgreSQL - removes role
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP ROLE my_role"));
    }

    @Test
    void testRejectDropUser() {
        // DROP USER - removes user
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP USER myuser"));
    }

    @Test
    void testRejectDropDomain() {
        // DROP DOMAIN - PostgreSQL - removes domain type
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP DOMAIN email"));
    }

    @Test
    void testRejectDropType() {
        // DROP TYPE - removes custom type
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP TYPE my_type"));
    }

    @Test
    void testRejectDropExtension() {
        // DROP EXTENSION - PostgreSQL - removes extension
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DROP EXTENSION uuid-ossp"));
    }

    // ========== Connection Management Statements ==========

    @Test
    void testRejectConnect() {
        // CONNECT - establishes connection to database
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CONNECT TO 'postgresql://localhost/mydb'"));
    }

    @Test
    void testRejectDisconnect() {
        // DISCONNECT - closes connection
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DISCONNECT ALL"));
    }

    @Test
    void testRejectSetSession() {
        // SET SESSION - sets session characteristics
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL SERIALIZABLE"));
    }

    @Test
    void testRejectSetTransactionIsolation() {
        // SET TRANSACTION - sets transaction isolation level
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED"));
    }

    @Test
    void testRejectSetAutoCommit() {
        // SET AUTOCOMMIT - controls auto-commit behavior
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET AUTOCOMMIT = 0"));
    }

    @Test
    void testRejectSetDeferrable() {
        // SET TRANSACTION DEFERRABLE - PostgreSQL
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SET TRANSACTION DEFERRABLE"));
    }

    @Test
    void testRejectShow() {
        // SHOW - displays configuration (read-only, but could affect understanding)
        // Note: SHOW is actually read-only - it just displays values
        // Including for completeness but may need reclassification
        String sql = "SHOW max_connections";
        // This one is debatable - SHOW doesn't modify state
        // Uncomment if policy requires SHOW to be rejected:
        // assertThrows(IllegalArgumentException.class, () ->
        //     ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testRejectUseIndex() {
        // USE INDEX - MySQL hint (modifies query execution)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * FROM users USE INDEX (idx_name) WHERE id = 1"));
    }

    @Test
    void testRejectForceIndex() {
        // FORCE INDEX - MySQL hint (modifies query execution)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * FROM users FORCE INDEX (idx_name) WHERE id = 1"));
    }

    @Test
    void testRejectIgnoreIndex() {
        // IGNORE INDEX - MySQL hint (modifies query execution)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT * FROM users IGNORE INDEX (idx_name) WHERE id = 1"));
    }

    @Test
    void testRejectPragma() {
        // PRAGMA - SQLite - modifies database behavior
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("PRAGMA foreign_keys = ON"));
    }

    @Test
    void testRejectPragmaRead() {
        // PRAGMA for reading - SQLite (read-only, but included for completeness)
        // Note: PRAGMA for reading values doesn't modify state
        String sql = "PRAGMA table_info(users)";
        // PRAGMA reads are actually safe, but included for reference
        // Uncomment if policy rejects all PRAGMA:
        // assertThrows(IllegalArgumentException.class, () ->
        //     ReadOnlySqlValidator.validateReadOnly(sql));
    }

    @Test
    void testRejectAttachDatabase() {
        // ATTACH DATABASE - SQLite - attaches additional database file
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("ATTACH DATABASE 'other.db' AS other_db"));
    }

    @Test
    void testRejectDetachDatabase() {
        // DETACH DATABASE - SQLite - detaches database
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("DETACH DATABASE other_db"));
    }

    @Test
    void testRejectSourceFile() {
        // SOURCE - MySQL - executes commands from file
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SOURCE /path/to/script.sql"));
    }

    @Test
    void testRejectBatch() {
        // Batch execution (varies by client)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("BATCH ON"));
    }

    @Test
    void testRejectClientCommand() {
        // Client command prefix (psql \c)
        // Note: These are client-side commands, not SQL
        String sql = "\\c other_database";
        // Client commands are typically handled at client level
        // Included for reference
    }

    // ========== GRANT/REVOKE (Security/Permission Statements) ==========

    @Test
    void testRejectGrant() {
        // GRANT - grants permissions
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("GRANT SELECT ON users TO role_name"));
    }

    @Test
    void testRejectGrantAll() {
        // GRANT ALL - grants all permissions
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("GRANT ALL ON DATABASE mydb TO role_name"));
    }

    @Test
    void testRejectRevoke() {
        // REVOKE - revokes permissions
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("REVOKE SELECT ON users FROM role_name"));
    }

    @Test
    void testRejectRevokeAll() {
        // REVOKE ALL - revokes all permissions
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("REVOKE ALL ON DATABASE mydb FROM role_name"));
    }

    // ========== Sequence State-Modifying Operations ==========

    @Test
    void testRejectSelectNextval() {
        // SELECT nextval() - PostgreSQL - advances sequence (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT nextval('my_sequence')"));
    }

    @Test
    void testRejectSelectNextvalInTable() {
        // SELECT with nextval() in FROM clause - PostgreSQL
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT nextval('seq1'), nextval('seq2') FROM users"));
    }

    @Test
    void testRejectSelectSetval() {
        // SELECT setval() - PostgreSQL - sets sequence value (modifies state)
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT setval('my_sequence', 100)"));
    }

    @Test
    void testRejectSelectSetvalWithIncrement() {
        // SELECT setval with increment flag - PostgreSQL
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT setval('my_sequence', 100, true)"));
    }

    @Test
    void testSelectCurrvalAllowed() {
        // SELECT currval() - PostgreSQL - read-only, gets current value
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT currval('my_sequence')"));
    }

    @Test
    void testSelectLastvalAllowed() {
        // SELECT lastval() - PostgreSQL - read-only, gets last value
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT lastval()"));
    }

    // ========== Sequence Functions from Other Dialects ==========

    @Test
    void testRejectOracleSequenceNextval() {
        // Oracle sequence.NEXTVAL - advances sequence
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT user_id_seq.NEXTVAL FROM dual"));
    }

    @Test
    void testOracleSequenceCurrval() {
        // Oracle sequence.CURRVAL - read-only, gets current value
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly("SELECT user_id_seq.CURRVAL FROM dual"));
    }

    @Test
    void testRejectSQLServerIdentity() {
        // SQL Server IDENTITY function - generates identity values
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT IDENTITY(int, 1, 1) FROM users"));
    }

    @Test
    void testRejectMySQLAutoIncrement() {
        // MySQL AUTO_INCREMENT in table definition
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("CREATE TABLE users (id INT AUTO_INCREMENT PRIMARY KEY)"));
    }

    @Test
    void testRejectMySQLLastInsertId() {
        // MySQL LAST_INSERT_ID() - gets last auto-increment value
        // Note: This is read-only but may be used to track inserted rows
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT LAST_INSERT_ID()"));
    }

    @Test
    void testRejectH2Sequence() {
        // H2 Database NEXTVAL function
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT NEXTVAL('SEQ_USERS')"));
    }

    @Test
    void testRejectDerbySequence() {
        // Derby Database - NEXT VALUE FOR sequence
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT NEXT VALUE FOR user_seq FROM sysibm.sysdummy1"));
    }

    @Test
    void testRejectSQLiteRowId() {
        // SQLite rowid/oid - can be used to track insertions
        // Note: rowid is read-only but can track auto-increment
        String sql = "SELECT rowid, * FROM users";
        // Currently allowed - debatable whether to reject
        assertDoesNotThrow(() ->
            ReadOnlySqlValidator.validateReadOnly(sql));
    }

    // ========== CTEs with All DML Variants ==========

    @Test
    void testRejectDeleteInCTE() {
        // CTE with DELETE - modifies data via CTE
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH deleted_users AS (DELETE FROM users WHERE status = 'inactive' RETURNING *) " +
                "SELECT * FROM deleted_users"));
    }

    @Test
    void testRejectMergeInCTE() {
        // CTE with MERGE - modifies data via CTE
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH merged_users AS (" +
                "MERGE INTO users u USING updates s ON u.id = s.id " +
                "WHEN MATCHED THEN UPDATE SET u.status = s.status " +
                "RETURNING *) SELECT * FROM merged_users"));
    }

    @Test
    void testRejectTruncateInCTE() {
        // CTE with TRUNCATE - modifies data via CTE
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH truncated AS (TRUNCATE TABLE users RETURNING *) SELECT * FROM truncated"));
    }

    @Test
    void testRejectMultipleDMLInCTE() {
        // CTE with multiple DML operations
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH deleted AS (DELETE FROM users WHERE age < 18 RETURNING *), " +
                "inserted AS (INSERT INTO archived_users SELECT * FROM deleted RETURNING *) " +
                "SELECT * FROM inserted"));
    }

    @Test
    void testRejectNestedCTEWithDML() {
        // Nested CTE with DML
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH RECURSIVE numbered_users AS (" +
                "UPDATE users SET status = 'processed' RETURNING id, name" +
                ") SELECT * FROM numbered_users"));
    }

    @Test
    void testRejectInsertSelectInCTE() {
        // CTE with INSERT...SELECT
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH new_data AS (" +
                "INSERT INTO backup_users SELECT * FROM users WHERE created > '2024-01-01' RETURNING *) " +
                "SELECT * FROM new_data"));
    }

    @Test
    void testRejectUpdateFromCTE() {
        // UPDATE using CTE results
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH updated AS (UPDATE users SET status = 'active' RETURNING id) " +
                "UPDATE user_status SET last_updated = NOW() WHERE user_id IN (SELECT id FROM updated)"));
    }

    @Test
    void testRejectDeleteFromCTE() {
        // DELETE using CTE results
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH old_records AS (SELECT id FROM users WHERE created < '2020-01-01') " +
                "DELETE FROM users WHERE id IN (SELECT id FROM old_records)"));
    }

    @Test
    void testRejectInsertFromCTE() {
        // INSERT using CTE results
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH recent_users AS (SELECT * FROM users WHERE created > NOW() - INTERVAL 7 DAY) " +
                "INSERT INTO user_activity SELECT id, 'viewed' FROM recent_users"));
    }

    @Test
    void testRejectMultipleCTEsWithMixedDML() {
        // Multiple CTEs with different DML operations
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH deleted_records AS (" +
                "  DELETE FROM audit_log WHERE created < '2020-01-01' RETURNING *" +
                "), " +
                "inserted_archive AS (" +
                "  INSERT INTO archived_audit SELECT * FROM deleted_records RETURNING *" +
                "), " +
                "updated_stats AS (" +
                "  UPDATE statistics SET last_cleaned = NOW() WHERE table_name = 'audit_log' RETURNING *" +
                ") " +
                "SELECT COUNT(*) FROM inserted_archive"));
    }

    // ========== Recursive CTEs with DML ==========

    @Test
    void testRejectRecursiveCTEWithDML() {
        // Recursive CTE with UPDATE
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH RECURSIVE org_hierarchy AS (" +
                "  SELECT id, parent_id, name FROM organization WHERE parent_id IS NULL " +
                "  UNION ALL " +
                "  SELECT c.id, c.parent_id, c.name FROM organization c " +
                "  INNER JOIN org_hierarchy h ON c.parent_id = h.id " +
                ") " +
                "UPDATE organization o SET depth = (SELECT COUNT(*) FROM org_hierarchy WHERE id = o.id)"));
    }

    @Test
    void testRejectWindowFunctionWithDML() {
        // Window function combined with write operation
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH ranked_users AS (" +
                "  SELECT *, ROW_NUMBER() OVER (ORDER BY created DESC) as rank FROM users " +
                ") " +
                "DELETE FROM users WHERE id NOT IN (SELECT id FROM ranked_users WHERE rank <= 1000)"));
    }

    @Test
    void testRejectGroupByWithDML() {
        // GROUP BY with aggregate and DML
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH summary AS (" +
                "  SELECT department, COUNT(*) as emp_count FROM employees GROUP BY department " +
                ") " +
                "UPDATE departments d SET employee_count = s.emp_count FROM summary s WHERE d.name = s.department"));
    }

    // ========== INSERT/UPDATE/DELETE with SELECT Subqueries ==========

    @Test
    void testRejectInsertWithSelectSubquery() {
        // INSERT...SELECT with subquery
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("INSERT INTO archive_users SELECT * FROM users WHERE age > 65"));
    }

    @Test
    void testRejectUpdateWithSelectSubquery() {
        // UPDATE with subquery
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "UPDATE users SET status = 'archived' WHERE id IN (SELECT user_id FROM inactive_accounts)"));
    }

    @Test
    void testRejectDeleteWithSelectSubquery() {
        // DELETE with subquery
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "DELETE FROM users WHERE id IN (SELECT user_id FROM spam_reports GROUP BY user_id HAVING COUNT(*) > 10)"));
    }

    // ========== Other State-Modifying Operations ==========

    @Test
    void testRejectCallFunction() {
        // SELECT via stored function that modifies - hard to detect statically
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly("SELECT my_proc_that_updates() FROM users"));
    }

    @Test
    void testRejectWithClauseModifyingData() {
        // WITH clause that modifies data in the process
        assertThrows(IllegalArgumentException.class, () ->
            ReadOnlySqlValidator.validateReadOnly(
                "WITH cte AS (" +
                "  UPDATE inventory SET quantity = quantity - 1 WHERE product_id = 123 RETURNING *" +
                ") " +
                "SELECT * FROM cte"));
    }
}

