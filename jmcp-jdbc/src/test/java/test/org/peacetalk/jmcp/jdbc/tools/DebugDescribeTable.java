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

package test.org.peacetalk.jmcp.jdbc.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

public class DebugDescribeTable {
    private static final Logger LOG = LogManager.getLogger(DebugDescribeTable.class);

    public static void main(String[] args) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:describeTableTest", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE users (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "email VARCHAR(255), " +
                "age INT DEFAULT 0" +
                ")");
        }

        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet rs = metaData.getColumns(null, null, "USERS", "%")) {
            int count = 0;
            while (rs.next()) {
                count++;
                LOG.info("Column {}: {} ({})", count, rs.getString("COLUMN_NAME"),
                    rs.getString("TYPE_NAME"));
            }
            LOG.info("Total columns: {}", count);
        }

        connection.close();
    }
}

