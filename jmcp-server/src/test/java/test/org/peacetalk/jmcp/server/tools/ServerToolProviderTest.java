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

package test.org.peacetalk.jmcp.server.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peacetalk.jmcp.core.Tool;
import org.peacetalk.jmcp.core.protocol.ResourcesHandler;
import org.peacetalk.jmcp.server.tools.ServerToolProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ServerToolProvider, which provides server-level tools.
 */
public class ServerToolProviderTest {

    private ResourcesHandler resourcesHandler;
    private ServerToolProvider provider;

    @BeforeEach
    public void setUp() {
        resourcesHandler = new ResourcesHandler();
        provider = new ServerToolProvider(resourcesHandler);
    }

    @Test
    public void testGetName() {
        assertEquals("Server Tools", provider.getName());
    }

    @Test
    public void testGetTools() {
        List<Tool> tools = provider.getTools();

        assertNotNull(tools);
        assertEquals(1, tools.size());

        Tool tool = tools.get(0);
        assertEquals("resource-proxy", tool.getName());
    }

    @Test
    public void testConfigure() {
        // Should not throw exception
        assertDoesNotThrow(() -> provider.configure(null));
    }

    @Test
    public void testShutdown() {
        // Should not throw exception
        assertDoesNotThrow(() -> provider.shutdown());
    }

    @Test
    public void testToolsAreSameInstance() {
        // Multiple calls to getTools() should return the same tool instances
        List<Tool> tools1 = provider.getTools();
        List<Tool> tools2 = provider.getTools();

        assertSame(tools1.get(0), tools2.get(0),
            "Should return the same tool instance on multiple calls");
    }

    @Test
    public void testConstructorAcceptsResourcesHandler() {
        // Verify constructor properly accepts ResourcesHandler
        assertDoesNotThrow(() -> new ServerToolProvider(resourcesHandler));
    }

    @Test
    public void testConstructorWithNullHandler() {
        // Constructor should accept null (though it may fail later when tool is used)
        assertDoesNotThrow(() -> new ServerToolProvider(null));
    }
}

