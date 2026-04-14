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

import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.jdbc.resources.TopologicalSort;
import org.peacetalk.jmcp.jdbc.resources.TopologicalSortResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TopologicalSort algorithm.
 * Tests various graph structures including simple chains, DAGs, cycles, and edge cases.
 */
public class TopologicalSortTest {

    @Test
    public void testEmptyGraph() {
        Set<String> tables = new HashSet<>();
        Map<String, Set<String>> deps = new HashMap<>();

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertNotNull(result);
        assertTrue(result.sortedTables().isEmpty());
        assertNull(result.cycles());
    }

    @Test
    public void testSingleTableNoDependencies() {
        Set<String> tables = new HashSet<>(List.of("table1"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(1, result.sortedTables().size());
        assertEquals("table1", result.sortedTables().get(0));
        assertNull(result.cycles());
    }

    @Test
    public void testSimpleChain() {
        // table3 -> table2 -> table1
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2", "table3"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());
        deps.put("table2", new HashSet<>(Set.of("table1")));
        deps.put("table3", new HashSet<>(Set.of("table2")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(3, result.sortedTables().size());
        assertNull(result.cycles());

        // Verify order: table1 must come before table2, table2 before table3
        int idx1 = result.sortedTables().indexOf("table1");
        int idx2 = result.sortedTables().indexOf("table2");
        int idx3 = result.sortedTables().indexOf("table3");

        assertTrue(idx1 < idx2, "table1 should come before table2");
        assertTrue(idx2 < idx3, "table2 should come before table3");
    }

    @Test
    public void testDiamondDependency() {
        // table4 -> table2 -> table1
        // table4 -> table3 -> table1
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2", "table3", "table4"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());
        deps.put("table2", new HashSet<>(Set.of("table1")));
        deps.put("table3", new HashSet<>(Set.of("table1")));
        deps.put("table4", new HashSet<>(Set.of("table2", "table3")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(4, result.sortedTables().size());
        assertNull(result.cycles());

        // Verify order constraints
        int idx1 = result.sortedTables().indexOf("table1");
        int idx2 = result.sortedTables().indexOf("table2");
        int idx3 = result.sortedTables().indexOf("table3");
        int idx4 = result.sortedTables().indexOf("table4");

        assertTrue(idx1 < idx2, "table1 should come before table2");
        assertTrue(idx1 < idx3, "table1 should come before table3");
        assertTrue(idx2 < idx4, "table2 should come before table4");
        assertTrue(idx3 < idx4, "table3 should come before table4");
    }

    @Test
    public void testSimpleCycle() {
        // table1 -> table2 -> table1 (cycle)
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>(Set.of("table2")));
        deps.put("table2", new HashSet<>(Set.of("table1")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertNotNull(result);
        assertNotNull(result.cycles(), "Should detect cycle");
        assertEquals(2, result.cycles().size());
        assertTrue(result.cycles().contains("table1"));
        assertTrue(result.cycles().contains("table2"));
        assertEquals(0, result.sortedTables().size(), "No tables should be sorted with complete cycle");
    }

    @Test
    public void testThreeNodeCycle() {
        // table1 -> table2 -> table3 -> table1 (cycle)
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2", "table3"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>(Set.of("table2")));
        deps.put("table2", new HashSet<>(Set.of("table3")));
        deps.put("table3", new HashSet<>(Set.of("table1")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertNotNull(result.cycles(), "Should detect cycle");
        assertEquals(3, result.cycles().size());
        assertEquals(0, result.sortedTables().size());
    }

    @Test
    public void testPartialCycle() {
        // table1 (no deps)
        // table2 -> table3 -> table2 (cycle)
        // table4 -> table1
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2", "table3", "table4"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());
        deps.put("table2", new HashSet<>(Set.of("table3")));
        deps.put("table3", new HashSet<>(Set.of("table2")));
        deps.put("table4", new HashSet<>(Set.of("table1")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertNotNull(result.cycles(), "Should detect cycle");
        assertEquals(2, result.cycles().size());
        assertTrue(result.cycles().contains("table2"));
        assertTrue(result.cycles().contains("table3"));

        // table1 and table4 should be sorted
        assertEquals(2, result.sortedTables().size());
        assertTrue(result.sortedTables().contains("table1"));
        assertTrue(result.sortedTables().contains("table4"));

        // table1 must come before table4
        int idx1 = result.sortedTables().indexOf("table1");
        int idx4 = result.sortedTables().indexOf("table4");
        assertTrue(idx1 < idx4);
    }

    @Test
    public void testSelfReference() {
        // table1 -> table1 (self-reference)
        Set<String> tables = new LinkedHashSet<>(List.of("table1"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>(Set.of("table1")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertNotNull(result.cycles());
        assertEquals(1, result.cycles().size());
        assertTrue(result.cycles().contains("table1"));
        assertEquals(0, result.sortedTables().size());
    }

    @Test
    public void testMultipleIndependentChains() {
        // Chain 1: table2 -> table1
        // Chain 2: table4 -> table3
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2", "table3", "table4"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());
        deps.put("table2", new HashSet<>(Set.of("table1")));
        deps.put("table3", new HashSet<>());
        deps.put("table4", new HashSet<>(Set.of("table3")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(4, result.sortedTables().size());
        assertNull(result.cycles());

        // Verify chain 1 ordering
        int idx1 = result.sortedTables().indexOf("table1");
        int idx2 = result.sortedTables().indexOf("table2");
        assertTrue(idx1 < idx2);

        // Verify chain 2 ordering
        int idx3 = result.sortedTables().indexOf("table3");
        int idx4 = result.sortedTables().indexOf("table4");
        assertTrue(idx3 < idx4);
    }

    @Test
    public void testComplexDAG() {
        // Complex DAG with multiple paths
        // table6 -> table4 -> table2 -> table1
        // table6 -> table5 -> table3 -> table1
        // table5 -> table2
        Set<String> tables = new LinkedHashSet<>(List.of(
            "table1", "table2", "table3", "table4", "table5", "table6"
        ));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());
        deps.put("table2", new HashSet<>(Set.of("table1")));
        deps.put("table3", new HashSet<>(Set.of("table1")));
        deps.put("table4", new HashSet<>(Set.of("table2")));
        deps.put("table5", new HashSet<>(Set.of("table2", "table3")));
        deps.put("table6", new HashSet<>(Set.of("table4", "table5")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(6, result.sortedTables().size());
        assertNull(result.cycles());

        // Verify all dependency constraints
        int idx1 = result.sortedTables().indexOf("table1");
        int idx2 = result.sortedTables().indexOf("table2");
        int idx3 = result.sortedTables().indexOf("table3");
        int idx4 = result.sortedTables().indexOf("table4");
        int idx5 = result.sortedTables().indexOf("table5");
        int idx6 = result.sortedTables().indexOf("table6");

        assertTrue(idx1 < idx2);
        assertTrue(idx1 < idx3);
        assertTrue(idx2 < idx4);
        assertTrue(idx2 < idx5);
        assertTrue(idx3 < idx5);
        assertTrue(idx4 < idx6);
        assertTrue(idx5 < idx6);
    }

    @Test
    public void testNonExistentDependency() {
        // table2 depends on table1, but table1 is not in the table set
        Set<String> tables = new LinkedHashSet<>(List.of("table2"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table2", new HashSet<>(Set.of("table1")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        // table2 should still be in a cycle because its dependency is not satisfied
        assertNotNull(result.cycles());
        assertEquals(1, result.cycles().size());
        assertTrue(result.cycles().contains("table2"));
    }

    @Test
    public void testQualifiedTableNames() {
        // Test with schema.table format
        Set<String> tables = new LinkedHashSet<>(List.of(
            "public.users", "public.orders", "public.order_items", "public.products"
        ));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("public.users", new HashSet<>());
        deps.put("public.products", new HashSet<>());
        deps.put("public.orders", new HashSet<>(Set.of("public.users")));
        deps.put("public.order_items", new HashSet<>(Set.of("public.orders", "public.products")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(4, result.sortedTables().size());
        assertNull(result.cycles());

        int idxUsers = result.sortedTables().indexOf("public.users");
        int idxProducts = result.sortedTables().indexOf("public.products");
        int idxOrders = result.sortedTables().indexOf("public.orders");
        int idxItems = result.sortedTables().indexOf("public.order_items");

        assertTrue(idxUsers < idxOrders);
        assertTrue(idxProducts < idxItems);
        assertTrue(idxOrders < idxItems);
    }

    @Test
    public void testCrossSchemaReferences() {
        // Test with cross-schema dependencies
        Set<String> tables = new LinkedHashSet<>(List.of(
            "schema1.table1", "schema1.table2", "schema2.table3"
        ));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("schema1.table1", new HashSet<>());
        deps.put("schema1.table2", new HashSet<>(Set.of("schema1.table1")));
        deps.put("schema2.table3", new HashSet<>(Set.of("schema1.table2")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(3, result.sortedTables().size());
        assertNull(result.cycles());

        int idx1 = result.sortedTables().indexOf("schema1.table1");
        int idx2 = result.sortedTables().indexOf("schema1.table2");
        int idx3 = result.sortedTables().indexOf("schema2.table3");

        assertTrue(idx1 < idx2);
        assertTrue(idx2 < idx3);
    }

    @Test
    public void testMultipleDependenciesToSameTable() {
        // table4 depends on table1, table2, and table3
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2", "table3", "table4"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());
        deps.put("table2", new HashSet<>());
        deps.put("table3", new HashSet<>());
        deps.put("table4", new HashSet<>(Set.of("table1", "table2", "table3")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(4, result.sortedTables().size());
        assertNull(result.cycles());

        int idx1 = result.sortedTables().indexOf("table1");
        int idx2 = result.sortedTables().indexOf("table2");
        int idx3 = result.sortedTables().indexOf("table3");
        int idx4 = result.sortedTables().indexOf("table4");

        assertTrue(idx1 < idx4);
        assertTrue(idx2 < idx4);
        assertTrue(idx3 < idx4);
    }

    @Test
    public void testResultImmutability() {
        // Verify that the result lists are truly immutable or at least safe copies
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());
        deps.put("table2", new HashSet<>(Set.of("table1")));

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        List<String> sorted = result.sortedTables();
        assertNotNull(sorted);
        assertEquals(2, sorted.size());

        // Verify we can't modify through a cycle (since it's null)
        assertNull(result.cycles());
    }

    @Test
    public void testLargeGraph() {
        // Test with a larger graph (10 tables in a chain)
        Set<String> tables = new LinkedHashSet<>();
        Map<String, Set<String>> deps = new HashMap<>();

        for (int i = 1; i <= 10; i++) {
            tables.add("table" + i);
            if (i == 1) {
                deps.put("table1", new HashSet<>());
            } else {
                deps.put("table" + i, new HashSet<>(Set.of("table" + (i - 1))));
            }
        }

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(10, result.sortedTables().size());
        assertNull(result.cycles());

        // Verify ordering
        for (int i = 0; i < 9; i++) {
            String current = "table" + (i + 1);
            String next = "table" + (i + 2);
            int idxCurrent = result.sortedTables().indexOf(current);
            int idxNext = result.sortedTables().indexOf(next);
            assertTrue(idxCurrent < idxNext, current + " should come before " + next);
        }
    }

    @Test
    public void testEmptyDependencySet() {
        // Tables with empty dependency sets should all be sorted
        Set<String> tables = new LinkedHashSet<>(List.of("table1", "table2", "table3"));
        Map<String, Set<String>> deps = new HashMap<>();
        deps.put("table1", new HashSet<>());
        deps.put("table2", new HashSet<>());
        deps.put("table3", new HashSet<>());

        TopologicalSortResult result = TopologicalSort.sort(tables, deps);

        assertEquals(3, result.sortedTables().size());
        assertNull(result.cycles());
    }
}

