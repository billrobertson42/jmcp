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

module org.peacetalk.jmcp.client {
    requires org.peacetalk.jmcp.core;
    requires javafx.controls;
    requires javafx.fxml;
    requires tools.jackson.databind;
    requires java.prefs;
    requires org.apache.logging.log4j;

    exports org.peacetalk.jmcp.client;
    opens org.peacetalk.jmcp.client to javafx.fxml;

    exports org.peacetalk.jmcp.client.service to org.peacetalk.jmcp.client.test;
    exports org.peacetalk.jmcp.client.ui to org.peacetalk.jmcp.client.test;

}

