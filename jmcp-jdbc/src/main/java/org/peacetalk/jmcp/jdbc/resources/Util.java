package org.peacetalk.jmcp.jdbc.resources;

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
        return SCHEME + "://connection/" + connectionId;
    }

    public static String connectionSchemasUri(String connectionId) {
        return SCHEME + "://connection/" + connectionId + "/schemas";
    }

    public static String relationshipsUri(String connectionId) {
        return SCHEME + "://connection/" + connectionId + "/relationships";
    }

    // Schema level URIs
    public static String schemaUri(String connectionId, String schemaName) {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName;
    }

    public static String schemaTablesUri(String connectionId, String schemaName) {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/tables";
    }

    public static String schemaViewsUri(String connectionId, String schemaName) {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/views";
    }

    public static String schemaRelationshipsUri(String connectionId, String schemaName) {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/relationships";
    }

    // Table level URIs
    public static String tableUri(String connectionId, String schemaName, String tableName) {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/table/" + tableName;
    }

    // View level URIs
    public static String viewUri(String connectionId, String schemaName, String viewName) {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/view/" + viewName;
    }

    // Procedure level URIs
    public static String procedureUri(String connectionId, String schemaName, String procedureName) {
        return SCHEME + "://connection/" + connectionId + "/schema/" + schemaName + "/procedure/" + procedureName;
    }

    // Helper for URI prefix checking
    public static String schemePrefix() {
        return SCHEME + "://";
    }
}
