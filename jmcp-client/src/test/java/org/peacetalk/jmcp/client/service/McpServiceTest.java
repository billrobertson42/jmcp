package org.peacetalk.jmcp.client.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.model.Tool;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpService.
 */
class McpServiceTest {
    private McpService service;

    @BeforeEach
    void setUp() {
        service = new McpService();
    }

    @Test
    void testInitiallyNotConnected() {
        assertFalse(service.isConnected());
    }

    @Test
    void testDisconnectWhenNotConnected() {
        // Should not throw exception
        assertDoesNotThrow(() -> service.disconnect());
        assertFalse(service.isConnected());
    }

    @Test
    void testCleanupWhenNotConnected() {
        // Should not throw exception
        assertDoesNotThrow(() -> service.cleanup());
    }

    @Test
    void testGetServerInfoWhenNotConnected() {
        // Should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> service.getServerInfo());
    }

    @Test
    void testListToolsWhenNotConnected() {
        // Should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> service.listTools());
    }

    @Test
    void testExecuteToolWhenNotConnected() {
        // Should throw IllegalStateException
        assertThrows(IllegalStateException.class,
            () -> service.executeTool("test", java.util.Map.of()));
    }

    @Test
    void testSortToolsAlphabetically() {
        Tool tool1 = createTool("zebra", "Last alphabetically");
        Tool tool2 = createTool("alpha", "First alphabetically");
        Tool tool3 = createTool("middle", "Middle alphabetically");

        List<Tool> unsorted = Arrays.asList(tool1, tool2, tool3);
        List<Tool> sorted = service.sortTools(unsorted);

        assertEquals(3, sorted.size());
        assertEquals("alpha", sorted.get(0).name());
        assertEquals("middle", sorted.get(1).name());
        assertEquals("zebra", sorted.get(2).name());
    }

    @Test
    void testSortToolsCaseInsensitive() {
        Tool tool1 = createTool("Zebra", "Should be last");
        Tool tool2 = createTool("alpha", "Should be first");
        Tool tool3 = createTool("Middle", "Should be middle");

        List<Tool> unsorted = Arrays.asList(tool1, tool2, tool3);
        List<Tool> sorted = service.sortTools(unsorted);

        assertEquals(3, sorted.size());
        assertEquals("alpha", sorted.get(0).name());
        assertEquals("Middle", sorted.get(1).name());
        assertEquals("Zebra", sorted.get(2).name());
    }

    @Test
    void testSortToolsEmpty() {
        List<Tool> empty = List.of();
        List<Tool> sorted = service.sortTools(empty);

        assertTrue(sorted.isEmpty());
    }

    @Test
    void testSortToolsSingleItem() {
        Tool tool = createTool("only", "Only tool");
        List<Tool> single = List.of(tool);
        List<Tool> sorted = service.sortTools(single);

        assertEquals(1, sorted.size());
        assertEquals("only", sorted.get(0).name());
    }

    @Test
    void testSortToolsAlreadySorted() {
        Tool tool1 = createTool("a", "First");
        Tool tool2 = createTool("b", "Second");
        Tool tool3 = createTool("c", "Third");

        List<Tool> alreadySorted = Arrays.asList(tool1, tool2, tool3);
        List<Tool> sorted = service.sortTools(alreadySorted);

        assertEquals(3, sorted.size());
        assertEquals("a", sorted.get(0).name());
        assertEquals("b", sorted.get(1).name());
        assertEquals("c", sorted.get(2).name());
    }

    @Test
    void testSortToolsWithDuplicateNames() {
        Tool tool1 = createTool("same", "First");
        Tool tool2 = createTool("same", "Second");
        Tool tool3 = createTool("different", "Different");

        List<Tool> withDuplicates = Arrays.asList(tool1, tool2, tool3);
        List<Tool> sorted = service.sortTools(withDuplicates);

        assertEquals(3, sorted.size());
        assertEquals("different", sorted.get(0).name());
        // The two "same" tools should be at the end
        assertEquals("same", sorted.get(1).name());
        assertEquals("same", sorted.get(2).name());
    }

    @Test
    void testSortToolsPreservesOriginalList() {
        Tool tool1 = createTool("z", "Last");
        Tool tool2 = createTool("a", "First");

        List<Tool> original = Arrays.asList(tool1, tool2);
        List<Tool> sorted = service.sortTools(original);

        // Original list should be unchanged
        assertEquals("z", original.get(0).name());
        assertEquals("a", original.get(1).name());

        // Sorted list should be different
        assertEquals("a", sorted.get(0).name());
        assertEquals("z", sorted.get(1).name());
    }

    @Test
    void testMultipleDisconnects() {
        service.disconnect();
        service.disconnect(); // Second disconnect should not throw

        assertFalse(service.isConnected());
    }

    @Test
    void testCleanupCallsDisconnect() {
        // After cleanup, should not be connected
        service.cleanup();

        assertFalse(service.isConnected());
    }

    /**
     * Helper method to create a Tool for testing.
     */
    private Tool createTool(String name, String description) {
        // Create a simple JSON schema
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        tools.jackson.databind.node.ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        return new Tool(name, description, schema);
    }
}

