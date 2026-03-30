package org.peacetalk.jmcp.server.tools;

import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.ToolProvider;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;

import java.util.List;

/**
 * Tool provider for server-level tools like the resource proxy.
 * These tools are not tied to a specific domain (like JDBC) but provide
 * protocol-level functionality.
 */
public class ServerToolProvider implements ToolProvider {

    private final ResourceProxyTool resourceProxyTool;

    public ServerToolProvider(ResourcesHandler resourcesHandler) {
        this.resourceProxyTool = new ResourceProxyTool(resourcesHandler);
    }

    @Override
    public String getName() {
        return "Server Tools";
    }

    @Override
    public void initialize() {
        // No initialization needed
    }

    @Override
    public void shutdown() {
        // No shutdown needed
    }

    @Override
    public List<Tool> getTools() {
        return List.of(resourceProxyTool);
    }
}



