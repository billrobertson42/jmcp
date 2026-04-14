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
            "expose_urls", false,
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
    void testProviderInitialization() throws Exception {
        provider.configure(h2Config("test-db", "jdbc:h2:mem:test"));
        assertNotNull(provider);
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
        assertFalse(tools.isEmpty());

        assertTrue(tools.stream().anyMatch(t -> "query".equals(t.getName())),
            "Should have query tool");
        assertTrue(tools.stream().anyMatch(t -> "get-row-count".equals(t.getName())),
            "Should have get-row-count tool");
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
            "expose_urls", true,
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

