package test.org.peacetalk.jmcp.jdbc.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.tools.JdbcToolUtils;
import org.peacetalk.jmcp.jdbc.tools.results.ColumnMetadata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcToolUtilsTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:utilsTest", "sa", "");

        // Create test schema and tables
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA TEST_SCHEMA");
            stmt.execute("CREATE TABLE TEST_SCHEMA.PRODUCTS (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(100), " +
                "price DECIMAL(10,2)" +
                ")");
            stmt.execute("INSERT INTO TEST_SCHEMA.PRODUCTS VALUES (1, 'Widget', 19.99)");
            stmt.execute("INSERT INTO TEST_SCHEMA.PRODUCTS VALUES (2, 'Gadget', 29.99)");
            stmt.execute("INSERT INTO TEST_SCHEMA.PRODUCTS VALUES (3, 'Doohickey', 39.99)");

            stmt.execute("CREATE TABLE ORDERS (" +
                "order_id INT PRIMARY KEY, " +
                "customer_name VARCHAR(100), " +
                "total DECIMAL(10,2), " +
                "order_date DATE" +
                ")");
            stmt.execute("INSERT INTO ORDERS VALUES (1, 'Alice', 100.00, '2025-01-01')");
            stmt.execute("INSERT INTO ORDERS VALUES (2, 'Bob', 200.00, '2025-01-02')");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testResolveSchemaName_WithProvidedSchema() throws Exception {
        String result = JdbcToolUtils.resolveSchemaName(connection, "TEST_SCHEMA");
        assertEquals("TEST_SCHEMA", result);
    }

    @Test
    void testResolveSchemaName_WithNullSchema_UsesConnectionDefault() throws Exception {
        // H2 default schema is PUBLIC
        String result = JdbcToolUtils.resolveSchemaName(connection, null);
        assertNotNull(result);
        // H2 returns "PUBLIC" as the default schema
        assertEquals("PUBLIC", result);
    }

    @Test
    void testResolveSchemaName_WithNullSchema_AfterSettingSchema() throws Exception {
        connection.setSchema("TEST_SCHEMA");
        String result = JdbcToolUtils.resolveSchemaName(connection, null);
        assertEquals("TEST_SCHEMA", result);
    }

    @Test
    void testValidateTableExists_ValidTable_InExplicitSchema() throws Exception {
        // Should not throw
        assertDoesNotThrow(() ->
            JdbcToolUtils.validateTableExists(connection, "TEST_SCHEMA", "PRODUCTS"));
    }

    @Test
    void testValidateTableExists_ValidTable_InDefaultSchema() throws Exception {
        // Should not throw for table in PUBLIC schema
        assertDoesNotThrow(() ->
            JdbcToolUtils.validateTableExists(connection, "PUBLIC", "ORDERS"));
    }

    @Test
    void testValidateTableExists_ValidTable_WithNullSchema() throws Exception {
        // Should work with null schema for tables in default schema
        assertDoesNotThrow(() ->
            JdbcToolUtils.validateTableExists(connection, null, "ORDERS"));
    }

    @Test
    void testValidateTableExists_NonExistentTable_ThrowsException() {
        SQLException exception = assertThrows(SQLException.class, () ->
            JdbcToolUtils.validateTableExists(connection, "PUBLIC", "NONEXISTENT"));

        assertTrue(exception.getMessage().contains("NONEXISTENT"));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testValidateTableExists_NonExistentTable_InSchema_ThrowsException() {
        SQLException exception = assertThrows(SQLException.class, () ->
            JdbcToolUtils.validateTableExists(connection, "TEST_SCHEMA", "NONEXISTENT"));

        assertTrue(exception.getMessage().contains("NONEXISTENT"));
        assertTrue(exception.getMessage().contains("TEST_SCHEMA"));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testValidateTableExists_SqlInjectionAttempt_ThrowsException() {
        // Attempt SQL injection - should fail validation
        SQLException exception = assertThrows(SQLException.class, () ->
            JdbcToolUtils.validateTableExists(connection, null,
                "ORDERS; DROP TABLE ORDERS; --"));

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testBuildQualifiedTableName_WithSchema() {
        String result = JdbcToolUtils.buildQualifiedTableName("TEST_SCHEMA", "PRODUCTS");
        assertEquals("TEST_SCHEMA.PRODUCTS", result);
    }

    @Test
    void testBuildQualifiedTableName_WithNullSchema() {
        String result = JdbcToolUtils.buildQualifiedTableName(null, "ORDERS");
        assertEquals("ORDERS", result);
    }

    @Test
    void testBuildQualifiedTableName_WithEmptySchema() {
        String result = JdbcToolUtils.buildQualifiedTableName("", "ORDERS");
        assertEquals(".ORDERS", result);
    }

    @Test
    void testExtractColumnMetadata() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM orders")) {

            List<ColumnMetadata> columns = JdbcToolUtils.extractColumnMetadata(rs);

            assertNotNull(columns);
            assertEquals(4, columns.size());

            // Check column names
            assertEquals("ORDER_ID", columns.get(0).name());
            assertEquals("CUSTOMER_NAME", columns.get(1).name());
            assertEquals("TOTAL", columns.get(2).name());
            assertEquals("ORDER_DATE", columns.get(3).name());

            // Check types are present
            assertNotNull(columns.get(0).type());
            assertNotNull(columns.get(1).type());
            assertNotNull(columns.get(2).type());
            assertNotNull(columns.get(3).type());
        }
    }

    @Test
    void testExtractColumnMetadata_EmptyResultSet() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM orders WHERE 1=0")) {

            List<ColumnMetadata> columns = JdbcToolUtils.extractColumnMetadata(rs);

            assertNotNull(columns);
            assertEquals(4, columns.size()); // Metadata still available even if no rows
        }
    }

    @Test
    void testExtractRows_NoLimit() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM orders ORDER BY order_id")) {

            List<Map<String, Object>> rows = JdbcToolUtils.extractRows(rs);

            assertNotNull(rows);
            assertEquals(2, rows.size());

            // Check first row
            Map<String, Object> row1 = rows.get(0);
            assertEquals(1, row1.get("ORDER_ID"));
            assertEquals("Alice", row1.get("CUSTOMER_NAME"));

            // Check second row
            Map<String, Object> row2 = rows.get(1);
            assertEquals(2, row2.get("ORDER_ID"));
            assertEquals("Bob", row2.get("CUSTOMER_NAME"));
        }
    }

    @Test
    void testExtractRows_WithLimit() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM TEST_SCHEMA.PRODUCTS ORDER BY id")) {

            List<Map<String, Object>> rows = JdbcToolUtils.extractRows(rs, 2);

            assertNotNull(rows);
            assertEquals(2, rows.size()); // Should only get 2 rows even though there are 3

            // Check we got the first 2 rows
            assertEquals(1, rows.get(0).get("ID"));
            assertEquals(2, rows.get(1).get("ID"));
        }
    }

    @Test
    void testExtractRows_LimitLargerThanRowCount() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM ORDERS")) {

            List<Map<String, Object>> rows = JdbcToolUtils.extractRows(rs, 100);

            assertNotNull(rows);
            assertEquals(2, rows.size()); // Only 2 rows available
        }
    }

    @Test
    void testExtractRows_ZeroLimit_ReturnsAllRows() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM TEST_SCHEMA.PRODUCTS")) {

            List<Map<String, Object>> rows = JdbcToolUtils.extractRows(rs, 0);

            assertNotNull(rows);
            assertEquals(3, rows.size()); // All rows returned
        }
    }

    @Test
    void testExtractRows_NegativeLimit_ReturnsAllRows() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM TEST_SCHEMA.PRODUCTS")) {

            List<Map<String, Object>> rows = JdbcToolUtils.extractRows(rs, -1);

            assertNotNull(rows);
            assertEquals(3, rows.size()); // All rows returned
        }
    }

    @Test
    void testExtractRows_EmptyResultSet() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM ORDERS WHERE 1=0")) {

            List<Map<String, Object>> rows = JdbcToolUtils.extractRows(rs);

            assertNotNull(rows);
            assertTrue(rows.isEmpty());
        }
    }

    @Test
    void testExtractRows_WithNullValues() throws Exception {
        // Insert a row with null values
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO ORDERS VALUES (3, NULL, NULL, NULL)");
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM ORDERS WHERE order_id = 3")) {

            List<Map<String, Object>> rows = JdbcToolUtils.extractRows(rs);

            assertNotNull(rows);
            assertEquals(1, rows.size());

            Map<String, Object> row = rows.get(0);
            assertEquals(3, row.get("ORDER_ID"));
            assertNull(row.get("CUSTOMER_NAME"));
            assertNull(row.get("TOTAL"));
            assertNull(row.get("ORDER_DATE"));
        }
    }

    @Test
    void testExtractColumnMetadataAndRows_Together() throws Exception {
        // Test using both methods together (common pattern)
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM TEST_SCHEMA.PRODUCTS ORDER BY id")) {

            List<ColumnMetadata> columns = JdbcToolUtils.extractColumnMetadata(rs);
            List<Map<String, Object>> rows = JdbcToolUtils.extractRows(rs, 2);

            assertNotNull(columns);
            assertNotNull(rows);

            assertEquals(3, columns.size());
            assertEquals(2, rows.size());

            // Verify column names match row keys
            assertTrue(rows.get(0).containsKey("ID"));
            assertTrue(rows.get(0).containsKey("NAME"));
            assertTrue(rows.get(0).containsKey("PRICE"));
        }
    }

    @Test
    void testIntegrationScenario_ValidateAndBuildTableName() throws Exception {
        // Simulate complete workflow: resolve, validate, build
        String providedSchema = "TEST_SCHEMA";
        String tableName = "PRODUCTS";

        String resolvedSchema = JdbcToolUtils.resolveSchemaName(connection, providedSchema);
        assertEquals("TEST_SCHEMA", resolvedSchema);

        assertDoesNotThrow(() ->
            JdbcToolUtils.validateTableExists(connection, resolvedSchema, tableName));

        String qualifiedName = JdbcToolUtils.buildQualifiedTableName(resolvedSchema, tableName);
        assertEquals("TEST_SCHEMA.PRODUCTS", qualifiedName);

        // Verify we can actually query this table
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + qualifiedName)) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test
    void testIntegrationScenario_NullSchema_DefaultSchema() throws Exception {
        // Simulate workflow with null schema
        String tableName = "ORDERS";

        String resolvedSchema = JdbcToolUtils.resolveSchemaName(connection, null);
        assertEquals("PUBLIC", resolvedSchema);

        assertDoesNotThrow(() ->
            JdbcToolUtils.validateTableExists(connection, resolvedSchema, tableName));

        String qualifiedName = JdbcToolUtils.buildQualifiedTableName(resolvedSchema, tableName);
        assertEquals("PUBLIC.ORDERS", qualifiedName);
    }
}

