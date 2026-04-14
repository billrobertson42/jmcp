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

package org.peacetalk.jmcp.jdbc.tools.results;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Compact query result using arrays instead of objects for row data.
 * This significantly reduces token usage for LLM responses.
 *
 * Format:
 * {
 *   "table": "users",
 *   "schema": "public",
 *   "cols": ["col1", "col2", "col3"],
 *   "rows": [[val1, val2, val3], [val1, val2, val3]],
 *   "count": 10,
 *   "more": true
 * }
 *
 * Token savings vs verbose format: ~40-60% for typical result sets
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompactQueryResult(
    String table,
    String schema,
    @JsonProperty("cols") List<String> columns,
    @JsonProperty("rows") List<List<Object>> rows,
    @JsonProperty("count") int count,
    @JsonProperty("more") boolean hasMore
) {
    // Constructor for query tool (no table/schema context)
    public CompactQueryResult(List<String> columns, List<List<Object>> rows, int count, boolean hasMore) {
        this(null, null, columns, rows, count, hasMore);
    }

    // Constructor for sample tool (with table/schema context)
    public CompactQueryResult(String table, String schema, List<String> columns, List<List<Object>> rows, int count) {
        this(table, schema, columns, rows, count, false);
    }
}

