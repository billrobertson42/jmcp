@echo off
setlocal enabledelayedexpansion

REM Run the Java MCP Server

cd /d "%~dp0"

set MODULE_PATH=jmcp-server\target\jmcp-server-1.0.0-SNAPSHOT.jar
set MODULES_DIR=jmcp-server\target\dependency

for %%f in ("%MODULES_DIR%\*.jar") do (
    set MODULE_PATH=!MODULE_PATH!;%%f
)

REM Check if "debug" is in the arguments and replace with debug JVM args
set JVM_ARGS=
set PASS_ARGS=
:parse_args

if "%~1"=="" goto done_args
if "%~1"=="debug" (
  set JVM_ARGS=%JVM_ARGS% -agentlib:jdwp=transport=dt_socket,server=ysuspend=y,address=*:5005
) else (
  set PASS_ARGS=%PASS_ARGS% %~1
)
shift
goto parse_args
:done_args

REM Run with java using module path
REM --add-modules ALL-MODULE-PATH ensures ServiceLoader can resolve provider modules
REM (jmcp-jdbc and jmcp-transport-stdio are runtime-only dependencies, not required by jmcp-server)

java --module-path "%MODULE_PATH%" %JVM_ARGS% %PASS_ARGS% ^
    --add-modules ALL-MODULE-PATH ^
    --module org.peacetalk.jmcp.server/org.peacetalk.jmcp.server.Main

