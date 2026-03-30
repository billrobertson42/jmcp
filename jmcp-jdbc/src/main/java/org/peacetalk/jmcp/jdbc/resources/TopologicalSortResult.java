package org.peacetalk.jmcp.jdbc.resources;

import java.util.List;

/**
 * Result of topological sort operation.
 *
 * @param sortedTables Tables in dependency order (copy first to last)
 * @param cycles       Tables involved in circular dependencies (null if none)
 */
public record TopologicalSortResult(List<String> sortedTables, List<String> cycles) {
}
