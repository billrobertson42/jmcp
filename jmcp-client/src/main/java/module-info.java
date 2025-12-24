module org.peacetalk.jmcp.client {
    requires org.peacetalk.jmcp.core;
    requires javafx.controls;
    requires javafx.fxml;
    requires tools.jackson.databind;
    requires java.prefs;

    exports org.peacetalk.jmcp.client;

    opens org.peacetalk.jmcp.client to javafx.fxml;
}

