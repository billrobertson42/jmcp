# Future Enhancements Analysis

**Date:** January 4, 2026

---

## 1. Useful Tools Not Yet Implemented

### High Priority

#### explain-query
- **Purpose:** Show query execution plan
- **Why useful:** Helps LLMs understand query performance, suggest optimizations
- **Parameters:** `sql`, `database_id`, `format` (text/json/visual)
- **Database support:** All major databases support EXPLAIN

#### search-data
- **Purpose:** Full-text search across table data
- **Why useful:** Find specific values without knowing exact location
- **Parameters:** `search_term`, `tables` (optional), `columns` (optional), `database_id`
- **Implementation:** Use LIKE or database-specific full-text search

#### compare-schemas
- **Purpose:** Compare structure between two schemas or databases
- **Why useful:** Migration planning, version comparison, drift detection
- **Parameters:** `source_connection`, `target_connection`, `schema` (optional)
- **Returns:** Added/removed/modified tables, columns, constraints

### Medium Priority

#### validate-query
- **Purpose:** Check SQL syntax without executing
- **Why useful:** Validate generated queries before execution
- **Parameters:** `sql`, `database_id`
- **Implementation:** Use PREPARE without EXECUTE, or parser

#### find-duplicates
- **Purpose:** Find duplicate rows in a table
- **Why useful:** Data quality analysis
- **Parameters:** `table`, `columns`, `schema`, `database_id`

#### suggest-indexes
- **Purpose:** Recommend indexes based on query patterns or table structure
- **Why useful:** Performance optimization guidance
- **Parameters:** `table`, `schema`, `database_id`

#### data-lineage (if views/procedures exist)
- **Purpose:** Show dependencies between database objects
- **Why useful:** Impact analysis, understanding data flow
- **Parameters:** `object_name`, `object_type`, `database_id`

### Lower Priority

#### export-data
- **Purpose:** Export query results in various formats (CSV, JSON, SQL INSERT)
- **Considerations:** Security implications, output size limits

#### generate-sample-insert
- **Purpose:** Generate INSERT statements with realistic sample data
- **Why useful:** Testing, documentation, migrations

---

## 2. Useful Resources Not Yet Implemented

### High Priority

#### db://connection/{id}/indexes
- **Purpose:** List all indexes across all tables
- **Why useful:** Performance analysis, duplicate index detection
- **Content:** Index name, table, columns, type (btree, hash, etc.), unique flag

#### db://connection/{id}/schema/{schema}/procedures
- **Purpose:** List stored procedures and functions
- **Why useful:** Understanding available business logic
- **Content:** Name, parameters, return type, definition (if available)

#### db://connection/{id}/schema/{schema}/triggers
- **Purpose:** List triggers on tables
- **Why useful:** Understanding automatic behaviors
- **Content:** Name, table, timing (BEFORE/AFTER), events (INSERT/UPDATE/DELETE)

### Medium Priority

#### db://connection/{id}/statistics
- **Purpose:** Database-wide statistics overview
- **Content:** Total tables, total rows, total size, connection pool stats

#### db://connection/{id}/schema/{schema}/sequences
- **Purpose:** List sequences
- **Content:** Name, current value, increment, min/max

#### db://connection/{id}/users (if permitted)
- **Purpose:** List database users/roles
- **Content:** Name, privileges (read-only for security)

---

## 3. MCP Prompts Feature

### What Are MCP Prompts?

Prompts are pre-defined templates that help LLMs interact with the MCP server more effectively. They provide structured guidance for common tasks.

### MCP Prompt Specification

```typescript
interface Prompt {
  name: string;
  description: string;
  arguments?: PromptArgument[];
}

interface PromptArgument {
  name: string;
  description: string;
  required?: boolean;
}

interface GetPromptResult {
  description?: string;
  messages: PromptMessage[];
}
```

### Useful Prompts for Database MCP

#### 1. explore-database
```
Name: explore-database
Description: Guide for exploring an unfamiliar database
Arguments: 
  - database_id: Connection to explore

Messages:
  - Start by reading db://context to see all connections
  - For the specified connection, read db://connection/{id}/relationships to understand table relationships
  - Use sample-data tool to preview actual data in key tables
  - Use analyze-column to understand data distribution
```

#### 2. write-query
```
Name: write-query
Description: Help write a SQL query for a specific task
Arguments:
  - database_id: Target database
  - task: What the query should accomplish

Messages:
  - First, read relevant table resources to understand structure
  - Check foreign keys to understand joins needed
  - Use get-row-count to estimate result size
  - Write query, then use explain-query to optimize
```

#### 3. data-quality-check
```
Name: data-quality-check
Description: Analyze data quality in a table
Arguments:
  - database_id: Target database
  - table: Table to analyze
  - schema: Schema name

Messages:
  - Use analyze-column on each column to find nulls, distinct counts
  - Sample data to look for anomalies
  - Check for orphaned foreign keys
  - Report findings with recommendations
```

#### 4. understand-table
```
Name: understand-table
Description: Comprehensive analysis of a specific table
Arguments:
  - database_id: Target database
  - table: Table to understand
  - schema: Schema name

Messages:
  - Read table resource for structure
  - Check incoming and outgoing foreign keys
  - Get row count and statistics
  - Sample data to understand actual values
  - Analyze key columns
```

#### 5. migration-planning
```
Name: migration-planning
Description: Plan a database migration
Arguments:
  - source_connection: Source database
  - target_connection: Target database

Messages:
  - Compare schemas between source and target
  - Identify missing tables, columns, constraints
  - Check data type compatibility
  - Generate migration steps
```

### Implementation Approach

1. Create `Prompt` interface in jmcp-core
2. Create `PromptProvider` interface similar to ToolProvider
3. Implement `PromptsHandler` for prompts/list and prompts/get
4. Create `JdbcPromptProvider` with database-specific prompts
5. Register in server initialization

---

## 4. Other MCP Features Not Utilized

### 4.1 Sampling (Server → Client)

**What it is:** Server can request LLM completions from the client.

**Use cases for database MCP:**
- Generate human-readable descriptions of complex schemas
- Create documentation from database metadata
- Suggest column names or descriptions based on data patterns

**Implementation:** 
- `sampling/createMessage` method
- Server sends prompt, client returns completion

**Consideration:** Requires bidirectional communication, client must support it.

### 4.2 Logging

**What it is:** Structured logging from server to client for debugging.

**Use cases:**
- Log slow queries
- Log connection pool events
- Log validation warnings
- Debug tool execution

**Implementation:**
- `notifications/message` with level (debug, info, warning, error)
- Client displays in appropriate UI

**Current status:** Using stderr, could be enhanced with structured logging.

### 4.3 Resource Subscriptions

**What it is:** Client subscribes to resource changes, server sends notifications.

**Use cases:**
- Notify when table structure changes
- Notify when row count exceeds threshold
- Notify on connection pool issues

**Implementation:**
- `resources/subscribe` with URI
- `resources/unsubscribe`
- `notifications/resources/updated` when changes occur

**Consideration:** Requires polling database or triggers, adds complexity.

### 4.4 Roots

**What it is:** Server exposes root URIs for client to understand entry points.

**Use cases:**
- Tell client about db://connections as entry point
- Tell client about db://context as overview

**Implementation:**
- `roots/list` returns root URIs
- Already partially done with ContextResource

### 4.5 Progress Notifications

**What it is:** Server sends progress updates for long-running operations.

**Use cases:**
- Large query progress
- Bulk analysis progress
- Connection initialization progress

**Implementation:**
- `notifications/progress` with progress token, current, total

### 4.6 Cancellation

**What it is:** Client can cancel long-running requests.

**Use cases:**
- Cancel slow queries
- Cancel bulk operations

**Implementation:**
- `notifications/cancelled` with request ID
- Server must check cancellation flag during execution

---

## 5. Summary Table

### Current State

| Feature | Implemented | Notes |
|---------|------------|-------|
| Tools | ✅ 4 tools | query, get-row-count, sample-data, analyze-column |
| Resources | ✅ 9+ resources | Full schema navigation |
| Prompts | ❌ | Not implemented |
| Sampling | ❌ | Not implemented |
| Logging | ⚠️ Partial | Using stderr only |
| Subscriptions | ❌ | Not implemented |
| Roots | ❌ | Not implemented |
| Progress | ❌ | Not implemented |
| Cancellation | ❌ | Not implemented |

### Recommended Priorities

#### Phase 1: Immediate Value
1. **explain-query tool** - High value, easy to implement
2. **Prompts support** - Guides LLM behavior, improves UX
3. **Structured logging** - Better debugging

#### Phase 2: Enhanced Capabilities
4. **search-data tool** - Common need
5. **Procedures/triggers resources** - Complete schema picture
6. **Progress notifications** - Better UX for long operations

#### Phase 3: Advanced Features
7. **Resource subscriptions** - Real-time updates
8. **Sampling** - AI-assisted documentation
9. **Cancellation** - Better control

---

## 6. Implementation Considerations

### For Prompts

**Minimal implementation:**
```java
public interface Prompt {
    String getName();
    String getDescription();
    List<PromptArgument> getArguments();
    GetPromptResult getPrompt(Map<String, String> arguments);
}

public interface PromptProvider {
    List<Prompt> getPrompts();
}
```

**Handler methods:**
- `prompts/list` - Return all prompts
- `prompts/get` - Return specific prompt with filled arguments

### For Logging

**Use `notifications/message`:**
```java
public record LogMessage(
    String level,  // debug, info, warning, error
    String logger, // component name
    String message,
    Map<String, Object> data
) {}
```

### For Progress

**Use progress tokens:**
```java
public record Progress(
    String progressToken,
    long current,
    long total,
    String message
) {}
```

---

## 7. Questions for Decision

1. **Prompts:** Should prompts be hardcoded or configurable?
2. **Logging:** What log level should be default? Should it be configurable?
3. **Subscriptions:** Worth the complexity for database metadata that rarely changes?
4. **Sampling:** Is this useful enough to justify bidirectional communication?
5. **New tools:** Which tools provide most value for least implementation effort?
6. **Security:** Should some features be disabled by default (e.g., search-data)?

---

## 8. Conclusion

### Most Valuable Missing Features

1. **Prompts** - Guide LLM behavior, improve user experience
2. **explain-query tool** - Essential for query optimization
3. **Structured logging** - Better debugging and monitoring

### Deferred Features

- **Subscriptions** - Complexity vs. value for slowly-changing metadata
- **Sampling** - Interesting but niche use case
- **Cancellation** - Only needed for very long operations

### Recommendation

Focus on:
1. Adding prompts support (Phase 1)
2. Adding explain-query tool (Phase 1)
3. Enhancing logging (Phase 1)
4. Adding procedures/triggers resources (Phase 2)

This provides maximum value with reasonable implementation effort while keeping the codebase maintainable.

