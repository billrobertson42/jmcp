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

import java.util.List;

/**
 * Result of analyzing a column's data distribution.
 *
 * Provides comprehensive statistics about a column including:
 * - Total row count
 * - Distinct value count
 * - Null count
 * - Min/max values
 * - Most common values with frequencies
 */
public record ColumnAnalysis(
    String table,
    String schema,
    String column,
    long totalRows,
    long distinctCount,
    long nullCount,
    String minValue,
    String maxValue,
    List<ValueFrequency> topValues
) {}

