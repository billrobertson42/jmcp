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

package org.peacetalk.jmcp.server.tools;

import org.peacetalk.jmcp.core.McpProvider;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;

import java.util.List;
import java.util.Map;

/**
 * Tool provider for server-level tools like the resource proxy.
 * These tools are not tied to a specific domain (like JDBC) but provide
 * protocol-level functionality.
 */
public class ServerToolProvider implements McpProvider {

    private final ResourceProxyTool resourceProxyTool;

    public ServerToolProvider(ResourcesHandler resourcesHandler) {
        this.resourceProxyTool = new ResourceProxyTool(resourcesHandler);
    }

    @Override
    public String getName() {
        return "Server Tools";
    }

    @Override
    public void configure(Map<String, Object> config) {
        // No initialization needed — resource proxy is fully configured at construction
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
