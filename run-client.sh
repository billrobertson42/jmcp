#!/bin/bash

# Run the MCP Client GUI

cd "$(dirname "$0")"

# Build if needed
if [ ! -f "jmcp-client/target/jmcp-client-1.0-SNAPSHOT.jar" ]; then
    echo "Building project..."
    mvn clean package -DskipTests -pl jmcp-client -am
fi

# Run with JavaFX Maven plugin
exec mvn -X -pl jmcp-client javafx:run

