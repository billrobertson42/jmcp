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

package org.peacetalk.jmcp.core.transport;

/**
 * SPI interface for MCP transport providers.
 * Implementations are discovered via java.util.ServiceLoader.
 *
 * To register a transport provider, add to module-info.java:
 *   provides org.peacetalk.jmcp.core.transport.TransportProvider
 *       with com.example.MyTransportProvider;
 */
public interface TransportProvider {

    /** Human-readable name (e.g., "stdio", "sse", "websocket") */
    String getName();

    /** Create a new transport instance */
    McpTransport createTransport();

    /**
     * Priority for transport selection. Higher values = higher priority.
     * When multiple transports are available, the highest priority is used.
     * Default: 0
     */
    default int priority() {
        return 0;
    }
}

