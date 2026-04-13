#!/bin/bash

# Run the Java MCP Server

cd "$(dirname "$0")"

MODULE_PATH=jmcp-server/target/jmcp-server-1.0.0-SNAPSHOT.jar
MODULES_DIR=jmcp-server/target/dependency

for jarfile in "${MODULES_DIR}"/*.jar; do
    MODULE_PATH="${MODULE_PATH}:${jarfile}"
done

# Check if "debug" is in the arguments and replace with debug JVM args
JVM_ARGS=""
for arg in "$@"; do
    if [ "$arg" = "debug" ]; then
        JVM_ARGS="$JVM_ARGS -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
    else
        JVM_ARGS="$JVM_ARGS $arg"
    fi
done

# Run with java using module path
# --add-modules ALL-MODULE-PATH ensures ServiceLoader can resolve provider modules
# (jmcp-jdbc and jmcp-transport-stdio are runtime-only dependencies, not required by jmcp-server)
java --module-path "$MODULE_PATH" $JVM_ARGS \
     --add-modules ALL-MODULE-PATH \
     --module org.peacetalk.jmcp.server/org.peacetalk.jmcp.server.Main

