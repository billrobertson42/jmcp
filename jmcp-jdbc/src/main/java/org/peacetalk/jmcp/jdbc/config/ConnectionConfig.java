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

package org.peacetalk.jmcp.jdbc.config;

import java.util.Collections;
import java.util.List;

public record ConnectionConfig(
    String id,
    String databaseType,
    String jdbcUrl,
    String username,
    String password,
    List<String> schemaFilter
) {
    public ConnectionConfig {
        schemaFilter = (schemaFilter == null) ? Collections.emptyList() : Collections.unmodifiableList(schemaFilter);
    }

    public static ConnectionConfig basic(String id, String databaseType,
                                         String jdbcUrl, String username, String password) {
        return new ConnectionConfig(id, databaseType, jdbcUrl, username, password, Collections.emptyList());
    }
}
