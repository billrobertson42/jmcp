# Database Tools Summary

This document provides a summary of the available database interaction tools, their purpose, and security considerations.

### Security Overview

All tools that accept table or schema names as input (e.g., `describe-table`, `preview-table`) validate these names against the database metadata before use. This prevents SQL injection through object names.

Tools that execute raw SQL (`query`, `explain-query`) require special handling. The `query` tool uses a robust parser to ensure only read-only `SELECT` statements are executed. **It is critical that the `explain-query` tool be refactored to use this same validation logic to prevent malicious DDL/DML statements from being passed to the database.**

---

## Tool Reference

### 1. `list-connections`
- **Description**: Lists all configured database connections, including their ID, type, and a sanitized URL. The default connection ID is also identified.
- **Use Case**: Discovering available database connections to target with other tools.
- **Security**: Safe. Does not execute user input or expose credentials.

### 2. `list-schemas`
- **Description**: Lists all schemas (or catalogs in some databases) available in the current connection.
- **Use Case**: Discovering the top-level containers for tables and other database objects.
- **Security**: Safe. Uses `DatabaseMetaData` and does not execute raw user input.

### 3. `list-tables`
- **Description**: Lists all tables and views within a specific schema, or across all schemas if none is provided.
- **Use Case**: Discovering available tables and views to query or describe.
- **Security**: Safe. Uses `DatabaseMetaData` with pattern matching.

### 4. `list-views`
- **Description**: Lists all database views, optionally including their full SQL definitions. Can be filtered by schema.
- **Use Case**: Understanding pre-defined queries and business logic encapsulated in views.
- **Security**: Safe. Uses database-specific catalog queries.

### 5. `list-procedures`
- **Description**: Lists stored procedures and functions, optionally including their parameters and source code. Can be filtered by schema.
- **Use Case**: Discovering available stored logic that can be executed.
- **Security**: Safe. Uses database-specific catalog queries.

### 6. `describe-table`
- **Description**: Provides a comprehensive description of a table's structure. It returns columns, primary keys, foreign keys, and indexes by default. It can optionally include triggers, check constraints, table statistics, partitions, and extended column information (e.g., auto-increment).
- **Use Case**: Getting a complete picture of a table's schema, constraints, and behavior.
- **Security**: Safe. Validates table and schema names against metadata before use.

### 7. `preview-table`
- **Description**: Fetches the first N rows of a table (default 10, max 100) to provide a quick sample of its data.
- **Use Case**: Quickly inspecting the content of a table without running a full query.
- **Security**: Safe. Validates table and schema names against metadata. The row limit prevents large data exfiltration.

### 8. `get-row-count`
- **Description**: Returns the total number of rows in a specified table.
- **Use Case**: Getting a quick count of records in a table, often as a preliminary step before a larger query.
- **Security**: Safe. Validates table and schema names against metadata.

### 9. `query`
- **Description**: Executes a read-only `SELECT` query and returns the results. Supports parameterized queries to prevent SQL injection in `WHERE` clauses.
- **Use Case**: Retrieving data from the database using custom SQL.
- **Security**: High-risk tool made safe by a robust validator that ensures only `SELECT` statements can be run. It blocks all DML (`INSERT`, `UPDATE`, `DELETE`) and DDL (`CREATE`, `DROP`, `ALTER`) statements.

### 10. `explain-query`
- **Description**: Generates a query execution plan for a given `SELECT` statement.
- **Use Case**: Analyzing query performance and identifying opportunities for optimization.
- **Security**: **POTENTIAL RISK**. This tool accepts a raw SQL string. It **must** be refactored to use the same `ReadOnlySqlValidator` as the `query` tool to prevent execution of malicious non-`SELECT` statements.
