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

module org.peacetalk.jmcp.jdbc {
    requires org.peacetalk.jmcp.core;
    requires java.sql;
    requires tools.jackson.databind;
    requires net.sf.jsqlparser;
    requires org.apache.logging.log4j;

    // Public API - only what external modules (jmcp-server) need
    exports org.peacetalk.jmcp.jdbc;

    // Internal packages - only exported to test module
    exports org.peacetalk.jmcp.jdbc.config to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.driver to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.tools to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.tools.results to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.validation to org.peacetalk.jmcp.jdbc.test;
    exports org.peacetalk.jmcp.jdbc.resources to org.peacetalk.jmcp.jdbc.test;

    opens org.peacetalk.jmcp.jdbc.config;
    opens org.peacetalk.jmcp.jdbc.tools.results;
    opens org.peacetalk.jmcp.jdbc.resources;

    provides org.peacetalk.jmcp.core.McpProvider
        with org.peacetalk.jmcp.jdbc.JdbcMcpProvider;
}

