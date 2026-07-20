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

package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.routing.PathEncoding;
import tools.jackson.databind.ObjectMapper;

public class Util {
    public static final String SCHEME = "db";
    public static final ObjectMapper MAPPER = new ObjectMapper();

    // Root level URIs
    public static String connectionsUri() {
        return SCHEME + "://connections";
    }

    public static String contextUri() {
        return SCHEME + "://context";
    }

    // Connection level URIs
    public static String connectionUri(String connectionId) {
        return SCHEME + "://connection/" + encode(connectionId);
    }

    public static String connectionSchemasUri(String connectionId) {
        return SCHEME + "://connection/" + encode(connectionId) + "/schemas";
    }

    public static String relationshipsUri(String connectionId) {
        return SCHEME + "://connection/" + encode(connectionId) + "/relationships";
    }

    // Schema level URIs
    public static String schemaUri(String connectionId, String schemaName) {
        return SCHEME + "://connection/" + encode(connectionId) + "/schema/" + encode(schemaName);
    }

    public static String schemaTablesUri(String connectionId, String schemaName) {
        return SCHEME + "://connection/" + encode(connectionId) + "/schema/" + encode(schemaName) + "/tables";
    }

    public static String schemaViewsUri(String connectionId, String schemaName) {
        return SCHEME + "://connection/" + encode(connectionId) + "/schema/" + encode(schemaName) + "/views";
    }

    public static String schemaRelationshipsUri(String connectionId, String schemaName) {
        return SCHEME + "://connection/" + encode(connectionId) + "/schema/" + encode(schemaName) + "/relationships";
    }

    // Table level URIs
    public static String tableUri(String connectionId, String schemaName, String tableName) {
        return SCHEME + "://connection/" + encode(connectionId) + "/schema/" + encode(schemaName) + "/table/" + encode(tableName);
    }

    // View level URIs
    public static String viewUri(String connectionId, String schemaName, String viewName) {
        return SCHEME + "://connection/" + encode(connectionId) + "/schema/" + encode(schemaName) + "/view/" + encode(viewName);
    }

    // Procedure level URIs
    public static String procedureUri(String connectionId, String schemaName, String procedureName) {
        return SCHEME + "://connection/" + encode(connectionId) + "/schema/" + encode(schemaName) + "/procedure/" + encode(procedureName);
    }

    // Helper for URI prefix checking
    public static String schemePrefix() {
        return SCHEME + "://";
    }

    /**
     * Percent-encodes a dynamic path segment (connection id, schema/table/view/procedure name)
     * before it goes into a {@code db://} URI, so a name containing {@code /} (or any other
     * reserved character) round-trips correctly through {@code ResourceRoutes.resolve} instead
     * of being misread as an extra path segment.
     */
    private static String encode(String segment) {
        return PathEncoding.encodeSegment(segment);
    }

}
