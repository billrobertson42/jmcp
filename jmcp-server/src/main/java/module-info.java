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
