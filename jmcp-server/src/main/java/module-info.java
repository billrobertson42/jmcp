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

module org.peacetalk.jmcp.server {
    requires org.peacetalk.jmcp.core;
    requires tools.jackson.databind;
    requires org.apache.logging.log4j;

    // SPI consumption — server discovers providers at runtime
    uses org.peacetalk.jmcp.core.McpProvider;
    uses org.peacetalk.jmcp.core.transport.TransportProvider;

    // Jackson reflective access for tool result serialization
    opens org.peacetalk.jmcp.server.tools;

    // Assembly logic and server tools accessible to test module
    exports org.peacetalk.jmcp.server to org.peacetalk.jmcp.server.test;
    exports org.peacetalk.jmcp.server.tools to org.peacetalk.jmcp.server.test;
}
