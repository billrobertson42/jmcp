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

package test.org.peacetalk.jmcp.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.jdbc.JdbcMcpProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcMcpProviderTest {

    @TempDir
    Path tempDir;

    private JdbcMcpProvider provider;

    private static Map<String, Object> h2Config(String id, String dbUrl) {
        return Map.of(
            "default_id", id,
            "connections", List.of(Map.of(
                "id", id,
                "databaseType", "h2",
                "jdbcUrl", dbUrl,
                "username", "sa",
                "password", ""
            ))
        );
    }

    @BeforeEach
    void setUp() {
        provider = new JdbcMcpProvider();
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.shutdown();
        }
    }

    @Test
    void testProviderInitializationRegistersTools() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        // configure() must populate the tool list and the resource provider.
        assertFalse(provider.getTools().isEmpty(), "configure should register tools");
        assertNotNull(provider.getResourceProvider(), "configure should create a resource provider");
    }

    @Test
    void testGetToolsEmptyBeforeConfigure() {
        // Before configure(), no tools are registered — must be an empty list, not null.
        List<Tool> tools = provider.getTools();
        assertNotNull(tools, "getTools must never return null");
        assertTrue(tools.isEmpty(), "no tools should exist before configure()");
    }

    @Test
    void testGetResourceProviderNullBeforeConfigure() {
        assertNull(provider.getResourceProvider(),
            "resource provider should not exist before configure()");
    }

    @Test
    void testNullConnectionsKeyThrows() {
        // A config object present but with no 'connections' key maps to a null array,
        // which must be rejected just like an empty list.
        Map<String, Object> config = Map.of("default_id", "x");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> provider.configure(config));
        assertTrue(ex.getMessage().contains("no connections"),
            "missing connections key should surface the 'no connections' error");
    }

    @Test
    void testLegacyExposeUrlsFieldInConfigIsIgnored() throws Exception {
        // Upgrade compatibility: a config file written before expose_urls was
        // removed may still carry that key. JdbcConfiguration no longer declares
        // the field, but MAPPER.convertValue() tolerates unknown map entries, so
        // configure() must still succeed rather than rejecting an otherwise-valid
        // old config file. Would fail if JdbcConfiguration (or the mapper) were
        // changed to reject unknown properties.
        Map<String, Object> configWithStaleField = Map.of(
            "default_id", "test-db",
            "expose_urls", false,
            "connections", List.of(Map.of(
                "id", "test-db",
                "databaseType", "h2",
                "jdbcUrl", "jdbc:h2:mem:legacyconfig",
                "username", "sa",
                "password", ""
            ))
        );

        assertDoesNotThrow(() -> provider.configure(configWithStaleField),
            "a stale 'expose_urls' key from an old config file must not break configure()");
        assertFalse(provider.getTools().isEmpty(),
            "connections from the legacy config should still register tools");
    }

    @Test
    void testShutdownIsIdempotentBeforeConfigure() {
        // shutdown() with nothing configured must not throw (null-guards in place).
        assertDoesNotThrow(() -> provider.shutdown());
    }

    @Test
    void testNullConfigThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> provider.configure(null));
        assertTrue(ex.getMessage().contains("requires configuration"),
            "Error message should mention configuration requirement");
    }

    @Test
    void testEmptyConnectionsThrows() {
        Map<String, Object> config = Map.of(
            "connections", List.of()
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> provider.configure(config));
        assertTrue(ex.getMessage().contains("no connections"),
            "Error message should mention no connections");
    }

    @Test
    void testGetName() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        String name = provider.getName();
        assertNotNull(name);
        assertFalse(name.isBlank());
    }

    @Test
    void testGetTools() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));

        List<Tool> tools = provider.getTools();
        assertNotNull(tools);

        List<String> names = tools.stream().map(Tool::getName).toList();
        // All five JDBC tools registered in configure() must be exposed.
        assertTrue(names.contains("query"), "should have query tool");
        assertTrue(names.contains("explain-query"), "should have explain-query tool");
        assertTrue(names.contains("get-row-count"), "should have get-row-count tool");
        assertTrue(names.contains("sample-data"), "should have sample-data tool");
        assertTrue(names.contains("analyze-column"), "should have analyze-column tool");
        assertEquals(5, tools.size(), "exactly the five JDBC tools should be registered");
    }

    @Test
    void testGetToolsReturnsDefensiveCopy() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));

        List<Tool> tools = provider.getTools();
        int original = tools.size();
        tools.clear();  // mutate the returned list

        assertEquals(original, provider.getTools().size(),
            "getTools should return a copy; caller mutation must not affect the provider");
    }

    @Test
    void testGetResourceProvider() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        assertNotNull(provider.getResourceProvider());
    }

    @Test
    void testShutdown() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        assertDoesNotThrow(() -> provider.shutdown());
    }

    @Test
    void testMultipleInitializations() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        List<Tool> tools1 = provider.getTools();

        provider.shutdown();

        provider = new JdbcMcpProvider();
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test2"));
        List<Tool> tools2 = provider.getTools();

        assertEquals(tools1.size(), tools2.size());
    }

    @Test
    void testConfigurationWithMultipleConnections() throws Exception {
        Map<String, Object> config = Map.of(
            "default_id", "primary",
            "connections", List.of(
                Map.of("id", "primary", "databaseType", "h2",
                    "jdbcUrl", "jdbc:h2:mem:db1", "username", "sa", "password", ""),
                Map.of("id", "secondary", "databaseType", "h2",
                    "jdbcUrl", "jdbc:h2:mem:db2", "username", "sa", "password", "")
            )
        );

        provider.configure(config);

        List<Tool> tools = provider.getTools();
        assertFalse(tools.isEmpty());
    }

    @Test
    void testToolsHaveSchemas() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        for (Tool tool : provider.getTools()) {
            assertNotNull(tool.getInputSchema(),
                "Tool " + tool.getName() + " should have input schema");
        }
    }

    @Test
    void testToolsHaveDescriptions() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        for (Tool tool : provider.getTools()) {
            String description = tool.getDescription();
            assertNotNull(description, "Tool " + tool.getName() + " should have description");
            assertFalse(description.isBlank(), "Tool " + tool.getName() + " description should not be blank");
        }
    }

    @Test
    void testToolsHaveNames() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        for (Tool tool : provider.getTools()) {
            String name = tool.getName();
            assertNotNull(name);
            assertFalse(name.isBlank());
        }
    }

    @Test
    void testImplementsMcpProvider() {
        assertTrue(provider instanceof McpProvider,
            "JdbcMcpProvider should implement McpProvider interface");
    }

    @Test
    void testToolNamesUnique() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        List<Tool> tools = provider.getTools();
        List<String> names = tools.stream().map(Tool::getName).toList();
        assertEquals(names.size(), names.stream().distinct().count(),
            "Tool names should be unique");
    }
}

