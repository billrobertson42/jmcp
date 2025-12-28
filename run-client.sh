#!/bin/bash

# Run the MCP Client GUI

cd "$(dirname "$0")"

MODULE_PATH=jmcp-client/target/jmcp-client-1.0.0-SNAPSHOT.jar
MODULES_DIR=jmcp-client/target/dependency

for jarfile in "${MODULES_DIR}"/*.jar; do
    MODULE_PATH="${MODULE_PATH}:${jarfile}"
done

# Run with java using module path
java --module-path "$MODULE_PATH" $* \
    --module org.peacetalk.jmcp.client/org.peacetalk.jmcp.client.McpClientApp
