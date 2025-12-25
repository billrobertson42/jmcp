package test.peacetalk.jmcp.client.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.client.service.McpService;
import org.peacetalk.jmcp.core.model.Tool;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
        Tool zebra = createTool("Zebra", "Should be last");
        Tool alpha = createTool("alpha", "Should be first");
        Tool middle = createTool("Middle", "Should be middle");

        List<Tool> unsorted = Arrays.asList(zebra, alpha, middle);
        List<Tool> sorted = service.sortTools(unsorted);

        assertEquals(3, sorted.size());
        assertSame(alpha, sorted.get(0));
        assertSame(middle, sorted.get(1));
        assertSame(zebra, sorted.get(2));
    }

    @Test
    void testSortToolsAlreadySorted() {
        Tool a = createTool("a", "First");
        Tool b = createTool("b", "Second");
        Tool c = createTool("c", "Third");

        List<Tool> alreadySorted = Arrays.asList(a, b, c);
        List<Tool> sorted = service.sortTools(alreadySorted);

        assertEquals(3, sorted.size());
        assertSame(a, sorted.get(0));
        assertSame(b, sorted.get(1));
        assertSame(c, sorted.get(2));
    }

    @Test
    void testSortToolsWithDuplicateNames() {
        Tool second = createTool("same", "First");
        Tool third = createTool("same", "Second");
        Tool first = createTool("different", "Different");

        List<Tool> withDuplicates = Arrays.asList(second, third, first);
        List<Tool> sorted = service.sortTools(withDuplicates);

        assertEquals(3, sorted.size());
        assertSame(first, sorted.get(0));
        // The two "same" tools should be at the end - this assumes a stable sort.
        assertSame(second, sorted.get(1));
        assertSame(third, sorted.get(2));
    }

    @Test
    void testSortToolsPreservesOriginalList() {
        Tool z = createTool("z", "Last");
        Tool a = createTool("a", "First");

        List<Tool> original = Arrays.asList(z, a);
        List<Tool> sorted = service.sortTools(original);

        // Original list should be unchanged
        assertSame(z, original.get(0));
        assertSame(a, original.get(1));

        // Sorted list should be different
        assertSame(a, sorted.get(0));
        assertSame(z, sorted.get(1));
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
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        return new Tool(name, description, schema);
    }
}

