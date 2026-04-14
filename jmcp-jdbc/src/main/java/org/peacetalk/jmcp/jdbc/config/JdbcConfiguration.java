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

public record JdbcConfiguration(
    String default_id,
    Boolean expose_urls,
    ConnectionConfig[] connections
) {
    public JdbcConfiguration {
        // Set default if not provided
        if (default_id == null || default_id.isBlank()) {
            default_id = "default";
        }
        // Default to false for security
        if (expose_urls == null) {
            expose_urls = false;
        }
    }
}

