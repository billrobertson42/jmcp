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

package test.org.peacetalk.jmcp.jdbc.resources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.ConnectionContext;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.resources.RelationshipsResource;
import org.peacetalk.jmcp.jdbc.resources.SchemaRelationshipsResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.peacetalk.jmcp.jdbc.resources.Util.connectionUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.schemaUri;
import static org.peacetalk.jmcp.jdbc.resources.Util.tableUri;

/**
 * Characterization tests for {@link RelationshipsResource} and
 * {@link SchemaRelationshipsResource}.
 *
 * These pin the exact current JSON output (relationship list, copyOrder, and
 * cyclesDetected -- in that field order and value) against a fixture that covers
 * three scenarios in one shot: multiple schemas, a cross-schema foreign key, and a
 * two-table cycle (SCHEMA_A.NODE_A -&gt; SCHEMA_A.NODE_B -&gt; SCHEMA_A.NODE_A).
 *
 * This is the contract for the RelationshipGraphBuilder extraction described in
 * claude/RELATIONSHIPS_REFACTOR_PLAN.md: every assertion here would fail if the
 * extraction changed a relationship's fields, copyOrder, or cycle detection for
 * either resource. They must pass unmodified both before and after that refactor.
 *
 * Notably, SCHEMA_B.INVOICES -&gt; SCHEMA_A.ORDERS is discovered differently by the
 * two resources: RelationshipsResource finds it while walking INVOICES's imported
 * keys, while SchemaRelationshipsResource("SCHEMA_A") only finds it while walking
 * ORDERS's *exported* keys. Both paths now agree on columns ORDER_ID -&gt; ID (see
 * {@code testSchemaRelationshipsResourceSchemaA}) -- the exported-keys path
 * previously reported ID -&gt; ORDER_ID, reversed relative to the relationship's own
 * fromTable/toTable direction; that bug is fixed in {@code RelationshipGraphBuilder}.
 */
class RelationshipGraphCharacterizationTest {

    private static final String CONNECTION_ID = "testdb";

    private Connection connection;
    private ConnectionManager mockConnectionManager;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        // No DB_CLOSE_DELAY=-1: each test method gets its own fresh in-memory
        // database, destroyed when the single connection below is closed in
        // tearDown, so the fixture can be recreated identically per test.
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:relationshipgraphtest;DB_CLOSE_DELAY=0", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS SCHEMA_A");
            stmt.execute("CREATE SCHEMA IF NOT EXISTS SCHEMA_B");

            stmt.execute("CREATE TABLE SCHEMA_A.CUSTOMERS (ID INT PRIMARY KEY, NAME VARCHAR(100))");
            stmt.execute("CREATE TABLE SCHEMA_A.ORDERS (ID INT PRIMARY KEY, CUSTOMER_ID INT, TOTAL DECIMAL(10,2))");
            stmt.execute("ALTER TABLE SCHEMA_A.ORDERS ADD CONSTRAINT FK_ORDERS_CUSTOMER " +
                    "FOREIGN KEY (CUSTOMER_ID) REFERENCES SCHEMA_A.CUSTOMERS(ID)");

            // Mutual cycle within SCHEMA_A: NODE_A <-> NODE_B.
            stmt.execute("CREATE TABLE SCHEMA_A.NODE_A (ID INT PRIMARY KEY, B_ID INT)");
            stmt.execute("CREATE TABLE SCHEMA_A.NODE_B (ID INT PRIMARY KEY, A_ID INT)");
            stmt.execute("ALTER TABLE SCHEMA_A.NODE_A ADD CONSTRAINT FK_NODE_A_B " +
                    "FOREIGN KEY (B_ID) REFERENCES SCHEMA_A.NODE_B(ID)");
            stmt.execute("ALTER TABLE SCHEMA_A.NODE_B ADD CONSTRAINT FK_NODE_B_A " +
                    "FOREIGN KEY (A_ID) REFERENCES SCHEMA_A.NODE_A(ID)");

            // Cross-schema FK: SCHEMA_B.INVOICES -> SCHEMA_A.ORDERS.
            stmt.execute("CREATE TABLE SCHEMA_B.INVOICES (ID INT PRIMARY KEY, ORDER_ID INT)");
            stmt.execute("ALTER TABLE SCHEMA_B.INVOICES ADD CONSTRAINT FK_INVOICES_ORDER " +
                    "FOREIGN KEY (ORDER_ID) REFERENCES SCHEMA_A.ORDERS(ID)");
        }

        mockConnectionManager = mock(ConnectionManager.class);
        ConnectionContext mockContext = mock(ConnectionContext.class);
        when(mockContext.getConnection()).thenReturn(connection);
        // Only SCHEMA_A / SCHEMA_B are "visible". H2's own PUBLIC/INFORMATION_SCHEMA
        // schemas must stay hidden so the "all schemas" resource's output is
        // deterministic and isn't drowned out by H2 catalog tables.
        when(mockContext.isSchemaVisible(anyString())).thenReturn(false);
        when(mockContext.isSchemaVisible("SCHEMA_A")).thenReturn(true);
        when(mockContext.isSchemaVisible("SCHEMA_B")).thenReturn(true);
        when(mockConnectionManager.getContext(CONNECTION_ID)).thenReturn(mockContext);

        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testRelationshipsResourceAcrossAllVisibleSchemas() throws Exception {
        RelationshipsResource resource = new RelationshipsResource(CONNECTION_ID, mockConnectionManager);

        JsonNode json = mapper.readTree(resource.read());

        // Would fail if extraction dropped a relationship, changed which schemas
        // participate, or altered a column mapping's direction.
        List<Map<String, Object>> expectedRelationships = List.of(
                relationship("FK_NODE_A_B", "SCHEMA_A", "NODE_A", "SCHEMA_A", "NODE_B",
                        col("B_ID", "ID")),
                relationship("FK_NODE_B_A", "SCHEMA_A", "NODE_B", "SCHEMA_A", "NODE_A",
                        col("A_ID", "ID")),
                relationship("FK_ORDERS_CUSTOMER", "SCHEMA_A", "ORDERS", "SCHEMA_A", "CUSTOMERS",
                        col("CUSTOMER_ID", "ID")),
                relationship("FK_INVOICES_ORDER", "SCHEMA_B", "INVOICES", "SCHEMA_A", "ORDERS",
                        col("ORDER_ID", "ID"))
        );
        assertEquals(expectedRelationships, relationshipsOf(json),
                "relationships must exactly match, in order, across both visible schemas");

        // Would fail if the topological sort order changed or a schema's tables
        // were dropped/reordered in copyOrder.
        assertEquals(List.of("SCHEMA_A.CUSTOMERS", "SCHEMA_A.ORDERS", "SCHEMA_B.INVOICES"),
                stringListOrNull(json.get("copyOrder")),
                "copyOrder must place CUSTOMERS before ORDERS before the cross-schema INVOICES");

        // Would fail if the NODE_A<->NODE_B cycle stopped being detected, or if a
        // non-cyclic table were wrongly flagged as part of the cycle.
        assertEquals(List.of("SCHEMA_A.NODE_A", "SCHEMA_A.NODE_B"),
                stringListOrNull(json.get("cyclesDetected")),
                "cyclesDetected must list exactly the two mutually-referencing tables");

        assertEquals(CONNECTION_ID, json.get("connectionId").asString());
        assertEquals(connectionUri(CONNECTION_ID), json.get("links").get("parent").asString());
    }

    @Test
    void testSchemaRelationshipsResourceSchemaA() throws Exception {
        SchemaRelationshipsResource resource =
                new SchemaRelationshipsResource(CONNECTION_ID, "SCHEMA_A", mockConnectionManager);

        JsonNode json = mapper.readTree(resource.read());

        // Would fail if extraction changed which relationships are found, or if the
        // exported-keys column-mapping direction regressed back to reversed (ID,
        // ORDER_ID) instead of matching the imported-keys convention.
        List<Map<String, Object>> expectedRelationships = List.of(
                relationship("FK_NODE_A_B", "SCHEMA_A", "NODE_A", "SCHEMA_A", "NODE_B",
                        col("B_ID", "ID")),
                relationship("FK_NODE_B_A", "SCHEMA_A", "NODE_B", "SCHEMA_A", "NODE_A",
                        col("A_ID", "ID")),
                relationship("FK_ORDERS_CUSTOMER", "SCHEMA_A", "ORDERS", "SCHEMA_A", "CUSTOMERS",
                        col("CUSTOMER_ID", "ID")),
                relationship("FK_INVOICES_ORDER", "SCHEMA_B", "INVOICES", "SCHEMA_A", "ORDERS",
                        col("ORDER_ID", "ID"))
        );
        assertEquals(expectedRelationships, relationshipsOf(json),
                "relationships must include the incoming cross-schema FK from SCHEMA_B.INVOICES, "
                        + "with columns in the same (fromColumn, toColumn) direction as the "
                        + "imported-keys path");

        // Would fail if the cross-schema FK to/from SCHEMA_B were (wrongly) counted
        // in the topological sort for this schema-scoped resource, or if INVOICES
        // leaked into copyOrder/allTables despite being outside SCHEMA_A.
        assertEquals(List.of("SCHEMA_A.CUSTOMERS", "SCHEMA_A.ORDERS"),
                stringListOrNull(json.get("copyOrder")),
                "copyOrder must only cover SCHEMA_A tables and ignore the cross-schema dependency");

        assertEquals(List.of("SCHEMA_A.NODE_A", "SCHEMA_A.NODE_B"),
                stringListOrNull(json.get("cyclesDetected")),
                "the intra-schema NODE_A<->NODE_B cycle must still be detected");

        assertEquals(CONNECTION_ID, json.get("connectionId").asString());
        assertEquals("SCHEMA_A", json.get("schema").asString());
        assertEquals(schemaUri(CONNECTION_ID, "SCHEMA_A"), json.get("links").get("parent").asString());
    }

    @Test
    void testSchemaRelationshipsResourceSchemaB() throws Exception {
        SchemaRelationshipsResource resource =
                new SchemaRelationshipsResource(CONNECTION_ID, "SCHEMA_B", mockConnectionManager);

        JsonNode json = mapper.readTree(resource.read());

        // Would fail if the outgoing cross-schema FK were dropped, or if its column
        // mapping direction differed from the imported-keys convention (this walks
        // INVOICES's own imported keys, same as the SCHEMA_A case above now does
        // for its exported-keys path).
        List<Map<String, Object>> expectedRelationships = List.of(
                relationship("FK_INVOICES_ORDER", "SCHEMA_B", "INVOICES", "SCHEMA_A", "ORDERS",
                        col("ORDER_ID", "ID"))
        );
        assertEquals(expectedRelationships, relationshipsOf(json),
                "the single outgoing cross-schema relationship must be present with unreversed columns");

        // Would fail if the (excluded, cross-schema) dependency on SCHEMA_A.ORDERS
        // were wrongly tracked, which would either add ORDERS to copyOrder or block
        // INVOICES from a clean, dependency-free sort.
        assertEquals(List.of("SCHEMA_B.INVOICES"), stringListOrNull(json.get("copyOrder")),
                "copyOrder must contain only the single SCHEMA_B table, with no cross-schema dependency");

        // Would fail if a phantom cycle were introduced for a schema with no
        // intra-schema cyclic references at all.
        assertNull(json.get("cyclesDetected").isNull() ? null : json.get("cyclesDetected"),
                "SCHEMA_B has no intra-schema cycle, so cyclesDetected must be null");

        assertEquals(CONNECTION_ID, json.get("connectionId").asString());
        assertEquals("SCHEMA_B", json.get("schema").asString());
        assertEquals(schemaUri(CONNECTION_ID, "SCHEMA_B"), json.get("links").get("parent").asString());
    }

    // --- helpers -----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> relationshipsOf(JsonNode json) {
        return (List<Map<String, Object>>) (List<?>) mapper.convertValue(json.get("relationships"), Object.class);
    }

    private List<String> stringListOrNull(JsonNode arrayNode) {
        if (arrayNode == null || arrayNode.isNull()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asString());
        }
        return result;
    }

    private static Map<String, Object> relationship(String name, String fromSchema, String fromTable,
                                                      String toSchema, String toTable,
                                                      Map<String, Object> singleColumn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("fromSchema", fromSchema);
        m.put("fromTable", fromTable);
        m.put("toSchema", toSchema);
        m.put("toTable", toTable);
        m.put("columns", List.of(singleColumn));
        m.put("fromTableUri", tableUri(CONNECTION_ID, fromSchema, fromTable));
        m.put("toTableUri", tableUri(CONNECTION_ID, toSchema, toTable));
        return m;
    }

    private static Map<String, Object> col(String fromColumn, String toColumn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fromColumn", fromColumn);
        m.put("toColumn", toColumn);
        return m;
    }
}
