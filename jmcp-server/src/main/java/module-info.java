module org.peacetalk.jmcp.server {
    requires org.peacetalk.jmcp.core;
    requires org.peacetalk.jmcp.transport.stdio;
    requires org.peacetalk.jmcp.jdbc;

    requires org.slf4j;
    requires tools.jackson.databind;

    // Export tools package for Jackson serialization of result records
    exports org.peacetalk.jmcp.server.tools;
}
