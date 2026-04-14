module org.peacetalk.jmcp.transport.stdio {
    requires org.peacetalk.jmcp.core;
    requires org.apache.logging.log4j;

    exports org.peacetalk.jmcp.transport.stdio;

    provides org.peacetalk.jmcp.core.transport.TransportProvider
        with org.peacetalk.jmcp.transport.stdio.StdioTransportProvider;
}

