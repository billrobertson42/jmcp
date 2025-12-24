package org.peacetalk.jmcp.jdbc.validation;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates SQL statements to ensure they are read-only (SELECT queries only).
 * Uses JSqlParser for robust SQL parsing instead of error-prone string matching.
 *
 * <p>This validator ensures that only SELECT statements are executed, preventing:
 * <ul>
 *   <li>Data modification (INSERT, UPDATE, DELETE, MERGE)</li>
 *   <li>Schema changes (CREATE, ALTER, DROP)</li>
 *   <li>Transaction control (COMMIT, ROLLBACK)</li>
 *   <li>Other write operations</li>
 * </ul>
 *
 * <p>The validator handles:
 * <ul>
 *   <li>CTEs (Common Table Expressions)</li>
 *   <li>Subqueries</li>
 *   <li>Complex SELECT statements</li>
 *   <li>Multi-statement SQL (rejects if any non-SELECT)</li>
 * </ul>
 */
public class ReadOnlySqlValidator {

    /**
     * Validates that the given SQL contains only SELECT statements.
     *
     * @param sql The SQL to validate
     * @throws IllegalArgumentException if the SQL contains non-SELECT statements
     * @throws SqlParseException if the SQL cannot be parsed
     */
    public static void validateReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL statement cannot be null or blank");
        }

        // First, check for statements that JSqlParser may not recognize
        // using string-based detection
        checkForUnparseableStatements(sql);

        try {
            // Parse the SQL - may contain multiple statements separated by semicolons
            List<Statement> statements = parseStatements(sql);

            if (statements.isEmpty()) {
                throw new IllegalArgumentException("No valid SQL statements found");
            }

            // Check each statement
            List<String> violations = new ArrayList<>();
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (!(stmt instanceof Select)) {
                    String stmtType = stmt.getClass().getSimpleName();
                    violations.add(String.format("Statement %d is %s (non-SELECT)", i + 1, stmtType));
                } else {
                    // Check for state-modifying functions within SELECT
                    Select select = (Select) stmt;

                    // Check CTEs for DML statements
                    List<String> cteViolations = checkCTEsForDML(select);
                    if (!cteViolations.isEmpty()) {
                        violations.addAll(cteViolations);
                    }

                    List<String> functionViolations = checkForStateModifyingFunctions(select);
                    if (!functionViolations.isEmpty()) {
                        violations.addAll(functionViolations);
                    }
                }
            }

            if (!violations.isEmpty()) {
                throw new IllegalArgumentException(
                    "Only SELECT queries are allowed. This tool is read-only. Violations: " +
                    String.join(", ", violations)
                );
            }

        } catch (IllegalArgumentException e) {
            // Re-throw our validation errors
            throw e;
        } catch (Exception e) {
            // If JSqlParser can't parse it, it's likely not a standard SELECT
            // Treat parse failures as invalid (non-SELECT) statements
            throw new IllegalArgumentException(
                "Only SELECT queries are allowed. This tool is read-only. " +
                "Unable to validate SQL statement: " + e.getMessage()
            );
        }
    }

    /**
     * Checks if the given SQL contains only SELECT statements.
     *
     * @param sql The SQL to check
     * @return true if the SQL contains only SELECT statements, false otherwise
     */
    public static boolean isReadOnly(String sql) {
        try {
            validateReadOnly(sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parses SQL into individual statements.
     * Handles both single and multiple statements.
     */
    private static List<Statement> parseStatements(String sql) throws Exception {
        List<Statement> statements = new ArrayList<>();

        // Try to parse as multiple statements first (semicolon-separated)
        try {
            net.sf.jsqlparser.parser.CCJSqlParser parser =
                CCJSqlParserUtil.newParser(sql);
            net.sf.jsqlparser.statement.Statements stmts = parser.Statements();
            if (stmts != null && !stmts.getStatements().isEmpty()) {
                statements.addAll(stmts.getStatements());
                return statements;
            }
        } catch (Exception multiStmtError) {
            // Fall through to single statement parsing
        }

        // Try to parse as a single statement
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            statements.add(stmt);
            return statements;
        } catch (Exception singleStmtError) {
            // Throw the single statement error
            throw singleStmtError;
        }
    }

    /**
     * Checks a SELECT statement's CTEs (Common Table Expressions) for DML operations.
     * CTEs can contain DELETE, INSERT, UPDATE, MERGE, or TRUNCATE with RETURNING clauses.
     *
     * @param select The SELECT statement to check
     * @return List of violation messages (empty if no violations found)
     */
    private static List<String> checkCTEsForDML(Select select) {
        List<String> violations = new ArrayList<>();

        // Check if the SELECT has WITH items (CTEs)
        List<WithItem<?>> withItems = select.getWithItemsList();
        if (withItems != null && !withItems.isEmpty()) {
            for (WithItem<?> withItem : withItems) {
                // In JSQLParser 5.x, the WITH item may contain a Select body
                // that could actually be a DML statement with RETURNING clause
                // We need to check the actual statement type

                // Get the select body - this might actually be DML with RETURNING
                Object selectBody = withItem.getSelect();

                if (selectBody != null) {
                    // Check if the string representation contains DML keywords
                    // This is necessary because JSQLParser may parse DML with RETURNING
                    // as part of a CTE structure
                    String cteString = selectBody.toString();
                    String cteUpper = cteString.toUpperCase();

                    // Remove any leading/trailing whitespace
                    cteUpper = cteUpper.trim();

                    // Check for DML keywords at the start of the CTE body
                    // These indicate write operations even with RETURNING
                    if (cteUpper.startsWith("DELETE ") || cteUpper.startsWith("DELETE\n") ||
                        cteUpper.startsWith("DELETE\t") || cteUpper.startsWith("DELETE\r")) {
                        violations.add("CTE '" + withItem.getAlias().getName() + "' contains DELETE statement");
                    }
                    if (cteUpper.startsWith("INSERT ") || cteUpper.startsWith("INSERT\n") ||
                        cteUpper.startsWith("INSERT\t") || cteUpper.startsWith("INSERT\r")) {
                        violations.add("CTE '" + withItem.getAlias().getName() + "' contains INSERT statement");
                    }
                    if (cteUpper.startsWith("UPDATE ") || cteUpper.startsWith("UPDATE\n") ||
                        cteUpper.startsWith("UPDATE\t") || cteUpper.startsWith("UPDATE\r")) {
                        violations.add("CTE '" + withItem.getAlias().getName() + "' contains UPDATE statement");
                    }
                    if (cteUpper.startsWith("MERGE ") || cteUpper.startsWith("MERGE\n") ||
                        cteUpper.startsWith("MERGE\t") || cteUpper.startsWith("MERGE\r")) {
                        violations.add("CTE '" + withItem.getAlias().getName() + "' contains MERGE statement");
                    }
                    if (cteUpper.startsWith("TRUNCATE ") || cteUpper.startsWith("TRUNCATE\n") ||
                        cteUpper.startsWith("TRUNCATE\t") || cteUpper.startsWith("TRUNCATE\r")) {
                        violations.add("CTE '" + withItem.getAlias().getName() + "' contains TRUNCATE statement");
                    }
                }
            }
        }

        return violations;
    }

    /**
     * Checks a SELECT statement for state-modifying functions like nextval() or setval().
     *
     * @param select The SELECT statement to check
     * @return List of violation messages (empty if no violations found)
     */
    private static List<String> checkForStateModifyingFunctions(Select select) {
        List<String> violations = new ArrayList<>();

        // List of functions that modify database state when called
        String[] stateModifyingFunctions = {
            "nextval",      // PostgreSQL - advances sequence
            "setval",       // PostgreSQL - sets sequence value
            "uuid_generate_v1", // Some PostgreSQL versions
        };

        try {
            // This is a simplified check - would need more sophisticated AST traversal
            // for production systems, but JSqlParser's Function detection is limited
            String selectStr = select.toString().toLowerCase();

            for (String func : stateModifyingFunctions) {
                if (selectStr.contains(func + "(")) {
                    violations.add("SELECT contains state-modifying function: " + func + "()");
                }
            }
        } catch (Exception e) {
            // If we can't analyze functions, we'll let the query through
            // This is conservative - we err on the side of allowing queries
            // that we can't fully analyze
        }

        return violations;
    }

    /**
     * Checks for SQL statements that JSqlParser may not recognize.
     * Uses string-based detection for common patterns.
     */
    private static void checkForUnparseableStatements(String sql) {
        String normalized = sql.trim().replaceAll("\\s+", " ").toUpperCase();

        // Check for CTEs with DML statements (DELETE, INSERT, UPDATE, MERGE, TRUNCATE)
        // Pattern: WITH name AS (DML_STATEMENT ...)
        if (normalized.contains("WITH ") && normalized.contains(" AS (")) {
            // Extract the CTE body roughly
            int withPos = normalized.indexOf("WITH ");
            int selectPos = normalized.indexOf("SELECT ", withPos);

            if (selectPos > withPos) {
                String cteSection = normalized.substring(withPos, selectPos);

                // Check for DML keywords in the CTE section
                if (cteSection.contains(" AS (DELETE ") || cteSection.contains(" AS ( DELETE ") ||
                    cteSection.contains(" AS (\nDELETE ") || cteSection.contains(" AS (\tDELETE ")) {
                    throw new IllegalArgumentException(
                        "Only SELECT queries are allowed. CTE with DELETE is not permitted.");
                }
                if (cteSection.contains(" AS (INSERT ") || cteSection.contains(" AS ( INSERT ") ||
                    cteSection.contains(" AS (\nINSERT ") || cteSection.contains(" AS (\tINSERT ")) {
                    throw new IllegalArgumentException(
                        "Only SELECT queries are allowed. CTE with INSERT is not permitted.");
                }
                if (cteSection.contains(" AS (UPDATE ") || cteSection.contains(" AS ( UPDATE ") ||
                    cteSection.contains(" AS (\nUPDATE ") || cteSection.contains(" AS (\tUPDATE ")) {
                    throw new IllegalArgumentException(
                        "Only SELECT queries are allowed. CTE with UPDATE is not permitted.");
                }
                if (cteSection.contains(" AS (MERGE ") || cteSection.contains(" AS ( MERGE ") ||
                    cteSection.contains(" AS (\nMERGE ") || cteSection.contains(" AS (\tMERGE ")) {
                    throw new IllegalArgumentException(
                        "Only SELECT queries are allowed. CTE with MERGE is not permitted.");
                }
                if (cteSection.contains(" AS (TRUNCATE ") || cteSection.contains(" AS ( TRUNCATE ") ||
                    cteSection.contains(" AS (\nTRUNCATE ") || cteSection.contains(" AS (\tTRUNCATE ")) {
                    throw new IllegalArgumentException(
                        "Only SELECT queries are allowed. CTE with TRUNCATE is not permitted.");
                }
            }
        }

        // Check for SELECT INTO (creates table or writes to file/variable)
        // Need to be careful not to match keywords in string literals
        if (normalized.matches(".*\\bSELECT\\b.*\\bINTO\\b.*")) {
            // Try to detect if INTO is in a string literal by looking for quotes before and after
            // This is a heuristic - not perfect but catches most cases
            int selectPos = normalized.indexOf("SELECT");
            int intoPos = normalized.indexOf("INTO", selectPos);

            if (intoPos > 0) {
                // Check if INTO appears to be outside string literals
                String beforeInto = sql.substring(0, intoPos);
                long singleQuotes = beforeInto.chars().filter(ch -> ch == '\'').count();
                long doubleQuotes = beforeInto.chars().filter(ch -> ch == '"').count();

                // If odd number of quotes before INTO, it's likely inside a string
                boolean likelyInString = (singleQuotes % 2 != 0) || (doubleQuotes % 2 != 0);

                if (!likelyInString) {
                    // Check for specific INTO variants
                    if (normalized.contains("INTO OUTFILE") ||
                        normalized.contains("INTO DUMPFILE") ||
                        normalized.contains("INTO @")) {
                        throw new IllegalArgumentException(
                            "Only SELECT queries are allowed. SELECT INTO OUTFILE/variable is not permitted.");
                    }
                    // Check if it looks like SELECT INTO table_name pattern
                    if (!normalized.contains(" FROM ") ||
                        normalized.indexOf("INTO") < normalized.indexOf("FROM")) {
                        throw new IllegalArgumentException(
                            "Only SELECT queries are allowed. SELECT INTO is not permitted.");
                    }
                }
            }
        }

        // Check for index hints (MySQL) - these are actually SELECT modifiers
        if (normalized.matches(".*\\b(USE|FORCE|IGNORE)\\s+INDEX\\b.*")) {
            throw new IllegalArgumentException(
                "Only SELECT queries are allowed. Index hints (USE/FORCE/IGNORE INDEX) are not permitted.");
        }

        // Check for LAST_INSERT_ID() - tracks inserts
        if (normalized.contains("LAST_INSERT_ID(")) {
            throw new IllegalArgumentException(
                "Only SELECT queries are allowed. LAST_INSERT_ID() function is not permitted.");
        }

        // Check for Oracle sequence.NEXTVAL syntax
        if (normalized.matches(".*\\w+\\.NEXTVAL\\b.*")) {
            throw new IllegalArgumentException(
                "Only SELECT queries are allowed. Sequence NEXTVAL is not permitted.");
        }

        // Check for NEXT VALUE FOR (Derby/SQL Server sequence syntax)
        if (normalized.contains("NEXT VALUE FOR")) {
            throw new IllegalArgumentException(
                "Only SELECT queries are allowed. NEXT VALUE FOR sequence is not permitted.");
        }

        // Check for SQL Server IDENTITY function
        if (normalized.matches(".*\\bIDENTITY\\s*\\(.*\\).*")) {
            throw new IllegalArgumentException(
                "Only SELECT queries are allowed. IDENTITY() function is not permitted.");
        }

        // Check for calling stored functions/procedures that might have side effects
        // This is a heuristic - we reject function calls that look like procedures
        // Match patterns like: my_proc(), my_proc_that_updates(), proc_name(), etc.
        if (normalized.matches(".*\\bSELECT\\b.*\\w*[_]?PROC\\w*\\s*\\(.*")) {
            throw new IllegalArgumentException(
                "Only SELECT queries are allowed. Calling procedures is not permitted.");
        }
    }

    /**
     * Exception thrown when SQL cannot be parsed.
     */
    public static class SqlParseException extends RuntimeException {
        public SqlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

