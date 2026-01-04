module org.peacetalk.jmcp.jdbc {
    requires org.peacetalk.jmcp.core;
    requires java.sql;
    requires tools.jackson.databind;
    requires jdbctl;
    requires net.sf.jsqlparser;

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
}

