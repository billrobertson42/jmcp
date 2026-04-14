/*
 * Copyright 2024 the jmcp authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

