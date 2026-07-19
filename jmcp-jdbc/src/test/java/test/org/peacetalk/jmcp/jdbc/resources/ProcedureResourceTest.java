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
import org.peacetalk.jmcp.jdbc.resources.ProcedureResource;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ProcedureResource}.
 *
 * <p>Uses an in-memory H2 database exposing a Java function via {@code CREATE ALIAS}, which H2
 * surfaces through {@code DatabaseMetaData.getProcedures}/{@code getProcedureColumns}. The
 * connection manager is mocked so the resource sees a real JDBC connection but a controllable
 * context.</p>
 *
 * <p>Note: {@code definition}, {@code language}, and {@code isDeterministic} are documented as
 * {@code null} on any dialect other than Postgres/MySQL/MariaDB (and Oracle/SQL Server for
 * {@code definition}). H2 is none of those, so these fields are expected to be {@code null}.</p>
 */
class ProcedureResourceTest {

    private Connection connection;
    private ConnectionManager mockConnectionManager;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:proceduretest;DB_CLOSE_DELAY=-1", "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS TEST_SCHEMA");
            stmt.execute("DROP ALIAS IF EXISTS TEST_SCHEMA.CALCULATE_TAX");
            stmt.execute("""
                CREATE ALIAS TEST_SCHEMA.CALCULATE_TAX AS $$
                    double calculateTax(double amount, double rate) {
                        return amount * rate;
                    }
                $$
            """);
        }

        mockConnectionManager = mock(ConnectionManager.class);
        ConnectionContext mockContext = mock(ConnectionContext.class);
        when(mockContext.getConnection()).thenReturn(connection);
        when(mockConnectionManager.getContext("testdb")).thenReturn(mockContext);
        when(mockConnectionManager.listConnections()).thenReturn(List.of(
            new ConnectionInfo("testdb", "sa", "h2")
        ));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP ALIAS IF EXISTS TEST_SCHEMA.CALCULATE_TAX");
                stmt.execute("DROP SCHEMA IF EXISTS TEST_SCHEMA");
            }
            connection.close();
        }
    }

    @Test
    void testMetadata() {
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "CALCULATE_TAX", mockConnectionManager);

        assertEquals("db://connection/testdb/schema/TEST_SCHEMA/procedure/CALCULATE_TAX", resource.getUri());
        assertEquals("Procedure: CALCULATE_TAX", resource.getName());
        assertFalse(resource.getDescription().isBlank(), "Description should be non-empty");
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void testReadReportsIdentityFields() throws Exception {
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "CALCULATE_TAX", mockConnectionManager);

        assertThatJson(resource.read()).and(
            j -> j.node("name").isEqualTo("CALCULATE_TAX"),
            j -> j.node("schema").isEqualTo("TEST_SCHEMA"),
            j -> j.node("connectionId").isEqualTo("testdb")
        );
    }

    @Test
    void testReadClassifiesFunctionType() throws Exception {
        // A CREATE ALIAS returning a value is reported by H2 as procedureReturnsResult,
        // which ProcedureResource maps to "FUNCTION". Guards the PROCEDURE_TYPE switch.
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "CALCULATE_TAX", mockConnectionManager);

        assertThatJson(resource.read())
            .node("type")
            .describedAs("A value-returning alias should be classified as FUNCTION, not PROCEDURE/UNKNOWN")
            .isEqualTo("FUNCTION");
    }

    @Test
    void testReadExposesParametersWithModes() throws Exception {
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "CALCULATE_TAX", mockConnectionManager);

        // Every parameter should carry a resolved (non-UNKNOWN) mode and an ordinal position.
        assertThatJson(resource.read())
            .node("parameters").isArray()
            .describedAs("calculateTax(amount, rate) should expose parameter metadata")
            .isNotEmpty()
            .allSatisfy(param -> assertThatJson(param).and(
                p -> p.node("name").isPresent(),
                p -> p.node("position").isPresent(),
                p -> p.node("mode").isString().isIn("IN", "OUT", "INOUT", "RETURN", "RESULT")
            ));
    }

    @Test
    void testReadLeavesDialectSpecificFieldsNullOnH2() throws Exception {
        // ProcedureResource only resolves definition/language/isDeterministic for specific dialects
        // (Postgres/MySQL/MariaDB, plus Oracle/SQL Server for definition). H2 is none of these,
        // so the fields must be null -- not the empty string and not a fabricated value.
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "CALCULATE_TAX", mockConnectionManager);

        assertThatJson(resource.read()).and(
            j -> j.node("definition").describedAs("definition should be null on H2").isNull(),
            j -> j.node("language").describedAs("language should be null on H2").isNull(),
            j -> j.node("isDeterministic").describedAs("isDeterministic should be null on H2").isNull()
        );
    }

    @Test
    void testReadLinksParentToSchema() throws Exception {
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "CALCULATE_TAX", mockConnectionManager);

        assertThatJson(resource.read())
            .node("links.parent")
            .describedAs("Parent link should point at the owning schema resource")
            .isEqualTo("db://connection/testdb/schema/TEST_SCHEMA");
    }

    @Test
    void testReadUnknownProcedureYieldsNullType() throws Exception {
        // No matching procedure -> getProcedures returns no rows, so type stays null and
        // parameters collapse to null (empty list is normalized to null in the response).
        ProcedureResource resource = new ProcedureResource("testdb", "TEST_SCHEMA", "DOES_NOT_EXIST", mockConnectionManager);

        assertThatJson(resource.read()).and(
            j -> j.node("name").isEqualTo("DOES_NOT_EXIST"),
            j -> j.node("type").describedAs("type should be null when no procedure matches").isNull(),
            j -> j.node("parameters").describedAs("parameters should be null when no procedure matches").isNull()
        );
    }
}
