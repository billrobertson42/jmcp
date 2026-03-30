package org.peacetalk.jmcp.jdbc.resources;

import java.util.*;

public class TopologicalSort {
    /**
     * Perform topological sort using Kahn's algorithm.
     * Tables are sorted so that dependencies come before dependents.
     *
     * @param allTables All table names to include in the sort
     * @param dependencyGraph Map of table -> set of tables it depends on (references)
     * @return Sorted tables and any detected cycles
     */
    public static TopologicalSortResult sort(Set<String> allTables,
                                             Map<String, Set<String>> dependencyGraph) {
        List<String> sorted = new ArrayList<>();
        List<String> cycles = new ArrayList<>();

        // Compute in-degree for each node (how many tables it depends on)
        Map<String, Integer> inDegree = new HashMap<>();
        for (String table : allTables) {
            inDegree.put(table, 0);
        }

        // Build reverse dependency map: table -> tables that depend on it
        Map<String, Set<String>> reverseDeps = new HashMap<>();
        for (String table : allTables) {
            reverseDeps.put(table, new HashSet<>());
        }

        // Count dependencies and build reverse map
        for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
            String dependent = entry.getKey();
            for (String dependency : entry.getValue()) {
                if (reverseDeps.containsKey(dependency)) {
                    reverseDeps.get(dependency).add(dependent);
                }
                // dependent depends on dependency, so dependent's inDegree++
                inDegree.merge(dependent, 1, Integer::sum);
            }
        }

        // Start with tables that have no dependencies
        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (visited.contains(current)) continue;
            visited.add(current);
            sorted.add(current);

            // For each table that depends on current, decrease its in-degree
            for (String dependent : reverseDeps.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newDegree);
                if (newDegree == 0 && !visited.contains(dependent)) {
                    queue.add(dependent);
                }
            }
        }

        // Check for cycles (tables not in sorted list)
        for (String table : allTables) {
            if (!visited.contains(table)) {
                cycles.add(table);
            }
        }

        return new TopologicalSortResult(sorted, cycles.isEmpty() ? null : cycles);
    }
}
