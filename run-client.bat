@echo off
setlocal enabledelayedexpansion

REM Run the MCP Client GUI

cd /d "%~dp0"

set MODULE_PATH=jmcp-client\target\jmcp-client-1.0.0-SNAPSHOT.jar
set MODULES_DIR=jmcp-client\target\dependency

for %%f in ("%MODULES_DIR%\*.jar") do (
    set MODULE_PATH=!MODULE_PATH!;%%f
)

REM Run with java using module path
java --module-path "%MODULE_PATH%" %* ^
     --enable-native-access=javafx.graphics ^
     --module org.peacetalk.jmcp.client/org.peacetalk.jmcp.client.McpClientApp

