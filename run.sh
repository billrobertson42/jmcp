#!/bin/bash

# Run the JDBC MCP Server

cd "$(dirname "$0")"

# Build if needed
if [ ! -f "jmcp-server/target/jmcp-server-1.0-SNAPSHOT.jar" ]; then
    echo "Building project..."
    mvn clean package -DskipTests
fi

# Run with Maven exec plugin to handle classpath
exec mvn -q -pl jmcp-server exec:java -Dexec.mainClass="org.peacetalk.jmcp.server.Main"

