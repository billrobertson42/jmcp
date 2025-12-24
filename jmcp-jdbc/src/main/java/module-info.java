module org.peacetalk.jmcp.jdbc {
    requires org.peacetalk.jmcp.core;
    requires java.sql;
    requires tools.jackson.databind;
    requires com.zaxxer.hikari;
    requires jdbctl;
    requires net.sf.jsqlparser;

    exports org.peacetalk.jmcp.jdbc;
    exports org.peacetalk.jmcp.jdbc.driver;
    exports org.peacetalk.jmcp.jdbc.tools;
    exports org.peacetalk.jmcp.jdbc.tools.results;
    exports org.peacetalk.jmcp.jdbc.validation to org.peacetalk.jmcp.jdbc.test;
}

