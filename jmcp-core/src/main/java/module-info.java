module org.peacetalk.jmcp.core {
    requires tools.jackson.databind;
    requires tools.jackson.core;
    requires jakarta.validation;
    requires org.hibernate.validator;
    requires org.apache.logging.log4j;

    exports org.peacetalk.jmcp.core.model;
    exports org.peacetalk.jmcp.core.protocol;
    exports org.peacetalk.jmcp.core.schema;
    exports org.peacetalk.jmcp.core.transport;
    exports org.peacetalk.jmcp.core.validation;
    exports org.peacetalk.jmcp.core;

    opens org.peacetalk.jmcp.core.schema;
    opens org.peacetalk.jmcp.core.protocol;
    opens org.peacetalk.jmcp.core.model;
}

