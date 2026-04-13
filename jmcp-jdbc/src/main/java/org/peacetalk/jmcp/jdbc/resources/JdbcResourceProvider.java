package org.peacetalk.jmcp.jdbc.resources;

import org.peacetalk.jmcp.core.Resource;
import org.peacetalk.jmcp.core.ResourceProvider;
import org.peacetalk.jmcp.jdbc.ConnectionManager;
import org.peacetalk.jmcp.jdbc.tools.results.ConnectionInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource provider for JDBC database resources.
 *
 * Provides a navigational/HATEOAS-like resource hierarchy:
 * <ul>
 *   <li>db://connections - List of all database connections</li>
 *   <li>db://connection/{id} - Details of a specific connection with links to sub-resources</li>
 *   <li>db://connection/{id}/schemas - List of schemas in the connection</li>
 *   <li>db://connection/{id}/schema/{schema} - Schema details with links to tables/views</li>
 *   <li>db://connection/{id}/schema/{schema}/tables - Tables in the schema</li>
 *   <li>db://connection/{id}/schema/{schema}/table/{table} - Table structure details</li>
 * </ul>
 */
public class JdbcResourceProvider implements ResourceProvider {

    private ConnectionManager connectionManager;

    /**
     * Set the connection manager to use for database operations
     */
    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void initialize() throws Exception {
        // Connection manager is set externally
        if (connectionManager == null) {
            throw new IllegalStateException("ConnectionManager must be set before initialization");
        }
    }

    @Override
    public List<Resource> listResources(String cursor) {
        // Return root-level resources - the list of connections
        List<Resource> resources = new ArrayList<>();

        // Add the context resource first - this is the primary resource for LLM consumption
        // It provides a comprehensive summary of all connections, schemas, tables, and available tools
        resources.add(new ContextResource(connectionManager));

        // Add the connections list resource for HATEOAS-style navigation
        resources.add(new ConnectionsListResource(connectionManager));

        // Add individual connection resources
        for (ConnectionInfo conn : connectionManager.listConnections()) {
            resources.add(new ConnectionResource(conn.id(), connectionManager));
        }

        return resources;
    }

    @Override
    public Resource getResource(String uri) {
        if (uri == null || !uri.startsWith(Util.SCHEME + "://")) {
            return null;
        }

        String path = uri.substring((Util.SCHEME + "://").length());
        String[] segments = path.split("/");

        // Parse the path and return the appropriate resource
        return switch (segments.length) {
            case 1 -> handleRootResource(segments[0]);
            case 2 -> handleConnectionResource(segments[0], segments[1]);
            case 3 -> handleSchemaLevelResource(segments[0], segments[1], segments[2]);
            case 4 -> handleObjectCollectionResource(segments[0], segments[1], segments[2], segments[3]);
            case 5 -> handleObjectResource(segments[0], segments[1], segments[2], segments[3], segments[4]);
            case 6 -> getResourceForSixSegments(segments[0], segments[1], segments[2], segments[3], segments[4], segments[5]);
            default -> null;
        };
    }

    /**
     * Handle root-level resources: connections, context
     */
    private Resource handleRootResource(String resourceType) {
        return switch (resourceType) {
            case "connections" -> new ConnectionsListResource(connectionManager);
            case "context" -> new ContextResource(connectionManager);
            default -> null;
        };
    }

    /**
     * Handle connection-level resources: connection/{id}, connection/{id}/schemas
     */
    private Resource handleConnectionResource(String type, String idOrName) {
        if (!"connection".equals(type)) {
            return null;
        }
        // Validate connection exists
        if (!connectionExists(idOrName)) {
            return null;
        }
        return new ConnectionResource(idOrName, connectionManager);
    }

    /**
     * Handle schema-level resources: connection/{id}/schemas, connection/{id}/relationships
     */
    private Resource handleSchemaLevelResource(String type, String connectionId, String schemaType) {
        if (!"connection".equals(type)) {
            return null;
        }
        if (!connectionExists(connectionId)) {
            return null;
        }

        return switch (schemaType) {
            case "schemas" -> new SchemasListResource(connectionId, connectionManager);
            case "relationships" -> new RelationshipsResource(connectionId, connectionManager);
            default -> null;
        };
    }

    /**
     * Handle object collection resources: connection/{id}/schema/{schema}/tables, views, etc.
     */
    private Resource handleObjectCollectionResource(String type, String connectionId,
                                                    String schemaType, String schemaName) {
        if (!"connection".equals(type) || !"schema".equals(schemaType)) {
            return null;
        }
        if (!connectionExists(connectionId)) {
            return null;
        }

        return new SchemaResource(connectionId, schemaName, connectionManager);
    }

    /**
     * Handle object collection resources: relationships
     */
    private Resource handleObjectResource(String type, String connectionId,
                                         String schemaType, String schemaName,
                                         String objectTypeOrCollection) {
        if (!"connection".equals(type) || !"schema".equals(schemaType)) {
            return null;
        }
        if (!connectionExists(connectionId)) {
            return null;
        }

        if ("relationships".equals(objectTypeOrCollection)) {
            return new SchemaRelationshipsResource(connectionId, schemaName, connectionManager);
        }
        return null;
    }

    /**
     * Handle specific object resources: table/{name}, view/{name}
     * URI pattern: db://connection/{id}/schema/{schema}/table/{tableName}
     */
    public Resource getResourceForSixSegments(String type, String connectionId,
                                               String schemaType, String schemaName,
                                               String objectType, String objectName) {
        if (!"connection".equals(type) || !"schema".equals(schemaType)) {
            return null;
        }
        if (!connectionExists(connectionId)) {
            return null;
        }

        return switch (objectType) {
            case "table" -> new TableResource(connectionId, schemaName, objectName, connectionManager);
            case "view" -> new ViewResource(connectionId, schemaName, objectName, connectionManager, Util.MAPPER);
            case "procedure" -> new ProcedureResource(connectionId, schemaName, objectName, connectionManager);
            default -> null;
        };
    }

    private boolean connectionExists(String connectionId) {
        return connectionManager.listConnections().stream()
            .anyMatch(conn -> conn.id().equals(connectionId));
    }

    @Override
    public boolean supportsScheme(String scheme) {
        return Util.SCHEME.equals(scheme);
    }

    @Override
    public void shutdown() {
        // Connection manager shutdown is handled by JdbcMcpProvider
    }

    @Override
    public String getName() {
        return "JDBC Database Resources";
    }
}

