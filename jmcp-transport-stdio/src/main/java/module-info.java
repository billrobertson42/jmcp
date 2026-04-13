module org.peacetalk.jmcp.transport.stdio {
    requires org.peacetalk.jmcp.core;

    exports org.peacetalk.jmcp.transport.stdio;

    provides org.peacetalk.jmcp.core.transport.TransportProvider
        with org.peacetalk.jmcp.transport.stdio.StdioTransportProvider;
}

