# Maven Surefire @{argLine} Fix - February 16, 2026

## Problem
Maven tests for jmcp-server were failing with:
```
The forked VM terminated without properly saying goodbye. VM crash or System.exit called?
Command was /bin/sh -c cd '/Users/bill/dev/mcp/jmcp/jmcp-server' && 
'/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home/bin/java' 
'@{argLine}' '-Dnet.bytebuddy.experimental=true' ...
```

The key issue: **`'@{argLine}'` was being passed literally** to the Java command instead of being interpolated.

## Root Cause
The `@{argLine}` placeholder is a special Maven property used by JaCoCo (code coverage tool) to inject its Java agent configuration. When JaCoCo is configured, it sets the `argLine` property, and the `@{argLine}` syntax allows combining JaCoCo's configuration with your own arguments.

**However, jmcp-server doesn't have JaCoCo configured**, so the `@{argLine}` placeholder is never replaced with anything, and Maven passes it literally as a command-line argument to Java, which fails.

## Solution
Remove the `@{argLine}` placeholder since JaCoCo is not configured for this module.

**Before:**
```xml
<configuration>
    <argLine>@{argLine}
        -Dnet.bytebuddy.experimental=true
        --add-opens java.base/java.lang=ALL-UNNAMED
        --add-opens java.base/java.lang.reflect=ALL-UNNAMED
        --add-opens java.base/java.util=ALL-UNNAMED
        --add-opens java.base/java.util.concurrent=ALL-UNNAMED
        --add-opens java.base/java.io=ALL-UNNAMED
    </argLine>
</configuration>
```

**After:**
```xml
<configuration>
    <argLine>-Dnet.bytebuddy.experimental=true
        --add-opens java.base/java.lang=ALL-UNNAMED
        --add-opens java.base/java.lang.reflect=ALL-UNNAMED
        --add-opens java.base/java.util=ALL-UNNAMED
        --add-opens java.base/java.util.concurrent=ALL-UNNAMED
        --add-opens java.base/java.io=ALL-UNNAMED
    </argLine>
</configuration>
```

## Why This Pattern Exists
Looking at other modules (like jmcp-jdbc), they use `@{argLine}` because they have JaCoCo configured:

```xml
<!-- In jmcp-jdbc/pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    ...
</plugin>
```

JaCoCo's `prepare-agent` goal sets the `argLine` property to something like:
```
-javaagent:/path/to/jacoco-agent.jar=destfile=/path/to/jacoco.exec
```

So when surefire sees `@{argLine}`, it gets expanded to include the JaCoCo agent.

## Why It Worked in IntelliJ
IntelliJ IDEA doesn't use Maven Surefire to run tests. It has its own test runner that doesn't process Maven's `argLine` configuration the same way. IntelliJ applies the necessary `--add-opens` arguments automatically when it detects JPMS modules and Mockito.

## Verification
After the fix, the Java command should look like:
```bash
java -Dnet.bytebuddy.experimental=true \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     ... 
     [test classes]
```

Instead of:
```bash
java '@{argLine}' -Dnet.bytebuddy.experimental=true ...  # ← Wrong!
```

## Related: If You Want Code Coverage
If you later want to add code coverage to jmcp-server, you would:

1. Add JaCoCo plugin to pom.xml
2. Restore the `@{argLine}` placeholder
3. Configure JaCoCo's prepare-agent goal

Example:
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>@{argLine} -Dnet.bytebuddy.experimental=true ...</argLine>
    </configuration>
</plugin>
```

Then the `@{argLine}` would be properly replaced by JaCoCo's agent configuration.

## Files Modified
- `/Users/bill/dev/mcp/jmcp/jmcp-server/pom.xml` - Removed `@{argLine}` placeholder from surefire configuration

