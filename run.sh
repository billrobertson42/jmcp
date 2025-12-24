#!/bin/bash

# Run the Java MCP Server

cd "$(dirname "$0")"

MODULE_PATH=jmcp-server/target/jmcp-server-1.0.0-SNAPSHOT.jar
MODULES_DIR=jmcp-server/target/dependency

for jarfile in "${MODULES_DIR}"/*.jar; do
    MODULE_PATH="${MODULE_PATH}:${jarfile}"
done

# Run with java using module path
java --module-path "$MODULE_PATH" $* \
    --module org.peacetalk.jmcp.server/org.peacetalk.jmcp.server.Main

