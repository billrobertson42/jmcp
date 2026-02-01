package test.org.peacetalk.jmcp.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.ToolProvider;
import org.peacetalk.jmcp.jdbc.JdbcToolProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JdbcToolProviderTest {

    @TempDir
    Path tempDir;

    private Path configFile;
    private JdbcToolProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, """
            {
              "default_id": "test-db",
              "expose_urls": false,
              "connections": [{
                "id": "test-db",
                "databaseType": "h2",
                "jdbcUrl": "jdbc:h2:mem:test",
                "username": "sa",
                "password": ""
              }]
            }
            """);

        // Set system property to use our config
        System.setProperty("jdbc.mcp.config", configFile.toString());
        provider = new JdbcToolProvider();
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.shutdown();
        }
        System.clearProperty("jdbc.mcp.config");
    }

    @Test
    void testProviderInitialization() throws Exception {
        provider.initialize();

        assertNotNull(provider);
    }

    @Test
    void testGetName() throws Exception {
        provider.initialize();

        String name = provider.getName();
        assertNotNull(name);
        assertFalse(name.isBlank());
    }

    @Test
    void testGetTools() throws Exception {
        provider.initialize();

        List<Tool> tools = provider.getTools();
        assertNotNull(tools);
        assertFalse(tools.isEmpty());

        // Should have standard tools
        assertTrue(tools.stream().anyMatch(t -> "query".equals(t.getName())),
            "Should have query tool");
        assertTrue(tools.stream().anyMatch(t -> "get-row-count".equals(t.getName())),
            "Should have get-row-count tool");
    }

    @Test
    void testGetResourceProvider() throws Exception {
        provider.initialize();

        var resourceProvider = provider.getResourceProvider();
        assertNotNull(resourceProvider);
    }

    @Test
    void testShutdown() throws Exception {
        provider.initialize();

        assertDoesNotThrow(() -> provider.shutdown());
    }

    @Test
    void testMultipleInitializations() throws Exception {
        provider.initialize();

        List<Tool> tools1 = provider.getTools();

        // Second initialization should work
        provider.shutdown();

        provider = new JdbcToolProvider();
        provider.initialize();

        List<Tool> tools2 = provider.getTools();

        assertEquals(tools1.size(), tools2.size());
    }

    @Test
    void testConfigurationLoading() throws Exception {
        // Create config with multiple connections
        Files.writeString(configFile, """
            {
              "default_id": "primary",
              "expose_urls": true,
              "connections": [{
                "id": "primary",
                "databaseType": "h2",
                "jdbcUrl": "jdbc:h2:mem:db1",
                "username": "sa",
                "password": ""
              },
              {
                "id": "secondary",
                "databaseType": "h2",
                "jdbcUrl": "jdbc:h2:mem:db2",
                "username": "sa",
                "password": ""
              }]
            }
            """);

        provider = new JdbcToolProvider();
        provider.initialize();

        List<Tool> tools = provider.getTools();
        assertFalse(tools.isEmpty());
    }

    @Test
    void testToolsHaveSchemas() throws Exception {
        provider.initialize();

        List<Tool> tools = provider.getTools();

        for (Tool tool : tools) {
            var schema = tool.getInputSchema();
            assertNotNull(schema, "Tool " + tool.getName() + " should have input schema");
        }
    }

    @Test
    void testToolsHaveDescriptions() throws Exception {
        provider.initialize();

        List<Tool> tools = provider.getTools();

        for (Tool tool : tools) {
            String description = tool.getDescription();
            assertNotNull(description, "Tool " + tool.getName() + " should have description");
            assertFalse(description.isBlank(), "Tool " + tool.getName() + " description should not be blank");
        }
    }

    @Test
    void testToolsHaveNames() throws Exception {
        provider.initialize();

        List<Tool> tools = provider.getTools();

        for (Tool tool : tools) {
            String name = tool.getName();
            assertNotNull(name);
            assertFalse(name.isBlank());
        }
    }

    @Test
    void testImplementsToolProvider() throws Exception {
        assertTrue(provider instanceof ToolProvider,
            "JdbcToolProvider should implement ToolProvider interface");
    }

    @Test
    void testMinimalConfiguration() throws Exception {
        // Test with minimal config
        Files.writeString(configFile, """
            {
              "connections": [{
                "id": "db",
                "databaseType": "h2",
                "jdbcUrl": "jdbc:h2:mem:test",
                "username": "sa",
                "password": ""
              }]
            }
            """);

        provider = new JdbcToolProvider();
        provider.initialize();

        List<Tool> tools = provider.getTools();
        assertFalse(tools.isEmpty());
    }

    @Test
    void testToolNamesUnique() throws Exception {
        provider.initialize();

        List<Tool> tools = provider.getTools();
        List<String> names = tools.stream()
            .map(Tool::getName)
            .toList();

        assertEquals(names.size(), names.stream().distinct().count(),
            "Tool names should be unique");
    }
}

