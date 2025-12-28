# Client Build and Execution Changes

**Date:** December 24, 2025

## Summary

Updated jmcp-client to match the pattern used by jmcp-server:
- Dependencies are copied during `package` phase
- `run-client.sh` uses direct `java` execution with module path instead of Maven

## Changes Made

### 1. Updated jmcp-client/pom.xml

Added `maven-dependency-plugin` to copy all dependencies to `target/dependency/` during package phase:

```xml
<!-- Copy dependencies for module path execution -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>3.9.0</version>
    <executions>
        <execution>
            <id>copy-dependencies</id>
            <phase>package</phase>
            <goals>
                <goal>copy-dependencies</goal>
            </goals>
            <configuration>
                <overWriteReleases>false</overWriteReleases>
                <overWriteSnapshots>false</overWriteSnapshots>
                <overWriteIfNewer>true</overWriteIfNewer>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 2. Updated run-client.sh

Changed from Maven execution to direct Java with module path:

**Before:**
```bash
if [ ! -f "jmcp-client/target/jmcp-client-1.0-SNAPSHOT.jar" ]; then
    echo "Building project..."
    mvn clean package -DskipTests -pl jmcp-client -am
fi

# Run with JavaFX Maven plugin
exec mvn -X -pl jmcp-client javafx:run
```

**After:**
```bash
if [ ! -f "jmcp-client/target/jmcp-client-1.0.0-SNAPSHOT.jar" ]; then
    mvn clean package -DskipTests -pl jmcp-client -am 2>&1 > /dev/null
fi

# Build module path from all jars
MODULE_PATH="jmcp-client/target/jmcp-client-1.0.0-SNAPSHOT.jar"
MODULE_PATH="$MODULE_PATH:jmcp-client/target/dependency/*"

# Run with java using module path
exec java --module-path "$MODULE_PATH" \
    --module org.peacetalk.jmcp.client/org.peacetalk.jmcp.client.McpClientApp
```

## Benefits

✅ **Faster startup** - No Maven overhead, direct Java execution
✅ **Consistent** - Matches server build pattern
✅ **Module-aware** - Uses proper JPMS module path
✅ **Cleaner output** - Suppresses build output, only shows app output
✅ **Proper main class** - Uses JPMS module syntax

## Module Path Construction

The script builds the module path in two parts:

1. **Client JAR:** `jmcp-client/target/jmcp-client-1.0.0-SNAPSHOT.jar`
2. **Dependencies:** `jmcp-client/target/dependency/*` (all dependency JARs)

This includes:
- `jmcp-core` (production dependency)
- `javafx-controls` (JavaFX controls)
- `javafx-fxml` (JavaFX FXML)
- `tools.jackson.databind` (JSON processing)
- All transitive dependencies

## Execution Command

```bash
java --module-path "$MODULE_PATH" \
    --module org.peacetalk.jmcp.client/org.peacetalk.jmcp.client.McpClientApp
```

This specifies:
- `--module-path` - Where to find JPMS modules
- `--module` - Which module and main class to run: `module/fully.qualified.ClassName`

## Running the Client

### Build and Run
```bash
./run-client.sh
```

### Build Only
```bash
mvn clean package -pl jmcp-client -am
```

### Run Existing Build
```bash
java --module-path "jmcp-client/target/jmcp-client-1.0.0-SNAPSHOT.jar:jmcp-client/target/dependency/*" \
    --module org.peacetalk.jmcp.client/org.peacetalk.jmcp.client.McpClientApp
```

## Consistency with Server

Both `run.sh` and `run-client.sh` now follow the same pattern:

1. ✅ Check if built JAR exists
2. ✅ Build with Maven if needed
3. ✅ Construct module path with dependencies
4. ✅ Execute with direct `java` command
5. ✅ Use `--module` syntax for JPMS

## Compilation Status

✅ **Compiles successfully**
✅ **Dependencies copied properly**
✅ **Script ready to use**

## Status

Both server and client now use the same efficient build and execution pattern! 🎉

