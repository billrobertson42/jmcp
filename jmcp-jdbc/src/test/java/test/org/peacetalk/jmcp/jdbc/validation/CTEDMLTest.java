package test.org.peacetalk.jmcp.jdbc.validation;

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.validation.ReadOnlySqlValidator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quick test to verify CTE DML detection works
 */
class CTEDMLTest {

    @Test
    void testCTEWithDelete() {
        String sql = "WITH deleted_users AS (DELETE FROM users WHERE status = 'inactive' RETURNING *) " +
                    "SELECT * FROM deleted_users";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> ReadOnlySqlValidator.validateReadOnly(sql));

        assertTrue(exception.getMessage().contains("DELETE") || exception.getMessage().contains("CTE"),
            "Exception should mention DELETE or CTE: " + exception.getMessage());
    }

    @Test
    void testCTEWithInsert() {
        String sql = "WITH new_data AS (" +
                    "INSERT INTO backup_users SELECT * FROM users WHERE created > '2024-01-01' RETURNING *) " +
                    "SELECT * FROM new_data";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> ReadOnlySqlValidator.validateReadOnly(sql));

        assertTrue(exception.getMessage().contains("INSERT") || exception.getMessage().contains("CTE"),
            "Exception should mention INSERT or CTE: " + exception.getMessage());
    }

    @Test
    void testCTEWithUpdate() {
        String sql = "WITH cte AS (" +
                    "  UPDATE inventory SET quantity = quantity - 1 WHERE product_id = 123 RETURNING *" +
                    ") " +
                    "SELECT * FROM cte";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> ReadOnlySqlValidator.validateReadOnly(sql));

        assertTrue(exception.getMessage().contains("UPDATE") || exception.getMessage().contains("CTE"),
            "Exception should mention UPDATE or CTE: " + exception.getMessage());
    }
}

