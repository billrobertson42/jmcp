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

import java.util.List;

/**
 * Result of describing a table structure
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TableDescription(
    String table,
    String schema,
    List<ColumnMetadata> columns,
    List<String> primaryKeys,
    List<ForeignKeyInfo> foreignKeys,
    List<IndexInfo> indexes,
    // Optional extended information
    List<TriggerInfo> triggers,
    List<CheckConstraintInfo> checkConstraints,
    TableStatistics statistics,
    PartitionInfo partitions
) {
}

