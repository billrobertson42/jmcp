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

