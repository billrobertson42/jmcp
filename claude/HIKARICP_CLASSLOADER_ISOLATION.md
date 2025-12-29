# HikariCP ClassLoader Isolation Fix

**Date:** December 28, 2025

## Problem

The original architecture had HikariCP loaded by the application classloader while JDBC drivers were loaded in isolated custom classloaders. This created several issues:

1. **ClassLoader Mismatch**: HikariCP (in app classloader) trying to work with Driver classes (in custom classloader)
2. **Thread Context ClassLoader Hacks**: Required constantly switching thread context classloader
3. **Not Truly Isolated**: Driver isolation was incomplete because the connection pool wasn't isolated
4. **Fragile**: Relied on context classloader switching which is error-prone

## Solution

Load HikariCP in the **same classloader** as the JDBC driver, achieving true isolation:

1. **Download HikariCP dynamically** from Maven Central (just like drivers)
2. **Include in driver classloader** - Both driver JAR and HikariCP JAR in same URLClassLoader
3. **Use reflection to instantiate** - Load and configure HikariCP via reflection
4. **Return standard interface** - Only expose `javax.sql.DataSource` interface (crosses classloader boundary safely)

## Implementation Changes

### 1. JdbcDriverManager - Include HikariCP in ClassLoader

**Before:**
```java
public static class DriverClassLoader extends URLClassLoader {
    public DriverClassLoader(Path jarPath) throws Exception {
        super(new URL[]{jarPath.toUri().toURL()}, 
              ClassLoader.getPlatformClassLoader());
    }
}
```

**After:**
```java
// HikariCP version to use with all drivers (6.x for Java 11+, 7.x requires Java 21+)
private static final DriverCoordinates HIKARI_CP = 
    new DriverCoordinates("com.zaxxer", "HikariCP", "6.2.1");

public static class DriverClassLoader extends URLClassLoader {
    public DriverClassLoader(Path driverJarPath, Path hikariJarPath) throws Exception {
        super(new URL[]{
            driverJarPath.toUri().toURL(),
            hikariJarPath.toUri().toURL()  // ← HikariCP now in same classloader
        }, ClassLoader.getPlatformClassLoader());
    }
}

public DriverClassLoader loadDriver(DriverCoordinates coordinates) throws Exception {
    String key = coordinates.toString();
    return loadedDrivers.computeIfAbsent(key, k -> {
        try {
            Path driverJarPath = downloadDriver(coordinates);
            Path hikariJarPath = downloadDriver(HIKARI_CP);  // ← Download HikariCP
            return new DriverClassLoader(driverJarPath, hikariJarPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load driver: " + coordinates, e);
        }
    });
}
```

**Version Notes:**
- **HikariCP 6.2.1** - Latest version compatible with Java 11+ (HikariCP 7.x requires Java 21+)
- **PostgreSQL 42.7.4** - Latest stable release (Dec 2024)
- **MySQL 9.1.0** - Latest connector (supports MySQL 5.7, 8.0, 8.4, 9.x)
- **MariaDB 3.5.1** - Latest stable release
- **Oracle 23.6.0.24.10** - Latest JDBC driver (Oct 2024)
- **SQL Server 12.8.1** - Latest release (jre11 variant for Java 11+)
- **H2 2.3.232** - Latest stable release
- **SQLite 3.47.1.0** - Latest JDBC driver (Dec 2024)

### 2. ConnectionManager - Remove HikariCP Compile-Time Dependency

**Before:**
```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

private final HikariDataSource dataSource;

// Thread context classloader switching
Thread currentThread = Thread.currentThread();
ClassLoader oldClassLoader = currentThread.getContextClassLoader();
try {
    currentThread.setContextClassLoader(classLoader);
    this.dataSource = new HikariDataSource(config);
} finally {
    currentThread.setContextClassLoader(oldClassLoader);
}
```

**After:**
```java
import javax.sql.DataSource;  // ← Standard interface only

private final DataSource dataSource;

// Load HikariCP via reflection from driver classloader
Class<?> hikariConfigClass = classLoader.loadClass("com.zaxxer.hikari.HikariConfig");
Class<?> hikariDataSourceClass = classLoader.loadClass("com.zaxxer.hikari.HikariDataSource");

// Create config and set properties via reflection
Object config = hikariConfigClass.getDeclaredConstructor().newInstance();
hikariConfigClass.getMethod("setJdbcUrl", String.class).invoke(config, jdbcUrl);
hikariConfigClass.getMethod("setUsername", String.class).invoke(config, username);
hikariConfigClass.getMethod("setPassword", String.class).invoke(config, password);
// ... more config via reflection

// Create HikariDataSource and cast to standard DataSource interface
this.dataSource = (DataSource) hikariDataSourceClass
    .getDeclaredConstructor(hikariConfigClass)
    .newInstance(config);
```

### 3. module-info.java - Remove HikariCP Requirement

**Before:**
```java
module org.peacetalk.jmcp.jdbc {
    requires org.peacetalk.jmcp.core;
    requires java.sql;
    requires tools.jackson.databind;
    requires com.zaxxer.hikari;  // ← Compile-time dependency
    requires jdbctl;
    requires net.sf.jsqlparser;
    // ...
}
```

**After:**
```java
module org.peacetalk.jmcp.jdbc {
    requires org.peacetalk.jmcp.core;
    requires java.sql;
    requires tools.jackson.databind;
    // HikariCP removed - loaded dynamically
    requires jdbctl;
    requires net.sf.jsqlparser;
    // ...
}
```

### 4. pom.xml - Remove HikariCP Dependency

**Before:**
```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

**After:**
```xml
<!-- HikariCP removed - downloaded dynamically from Maven Central -->
```

## Benefits

### 1. True Isolation
✅ **Complete ClassLoader Isolation**: Both driver and connection pool in same isolated classloader  
✅ **No Leakage**: Driver classes can't leak into application classloader  
✅ **Clean Unloading**: Entire classloader (driver + HikariCP) can be closed and GC'd  

### 2. No Thread Context ClassLoader Hacks
✅ **No Context Switching**: Don't need to manipulate thread context classloader  
✅ **Thread Safe**: No race conditions from context classloader switching  
✅ **Simpler Code**: Fewer try/finally blocks  

### 3. Works Across ClassLoader Boundaries
✅ **Standard Interfaces**: `javax.sql.DataSource` and `java.sql.Connection` are in `java.sql` module  
✅ **Safe to Cross**: Standard interfaces can be passed across classloader boundaries  
✅ **No ClassCastException**: Using interfaces, not concrete classes  

### 4. Dynamic Loading
✅ **No Compile Dependency**: HikariCP not in module-info or pom.xml  
✅ **Downloaded on Demand**: Only fetched when actually needed  
✅ **Version Control**: Can specify exact HikariCP version per driver if needed  

### 5. Reduced CVE Surface
✅ **Fewer Dependencies**: HikariCP not in application classpath  
✅ **Isolated Vulnerabilities**: HikariCP CVEs don't affect application layer  
✅ **Easy Updates**: Change `HIKARI_CP` constant to update version  

## How It Works

### ClassLoader Hierarchy

```
Bootstrap ClassLoader
    ↓
Platform ClassLoader (java.sql.*, javax.sql.*)
    ↓
Application ClassLoader (jmcp-jdbc module, but NOT HikariCP)
    ↓
Driver ClassLoader #1 (PostgreSQL driver + HikariCP 6.2.1)
    ↓
Driver ClassLoader #2 (MySQL driver + HikariCP 6.2.1)
    ↓
Driver ClassLoader #3 (Oracle driver + HikariCP 6.2.1)
```

### Interface Crossing

```
┌─────────────────────────────────────┐
│ Application ClassLoader             │
│                                     │
│  ConnectionManager                  │
│  holds: DataSource (interface)      │  ← javax.sql.DataSource from Platform ClassLoader
└─────────────────────────────────────┘
                ↓
┌─────────────────────────────────────┐
│ Driver ClassLoader                  │
│                                     │
│  HikariDataSource (impl)            │  ← Concrete class loaded here
│  implements DataSource              │
│                                     │
│  PostgreSQL Driver                  │
└─────────────────────────────────────┘
```

The `DataSource` **interface** is defined in `java.sql` module (Platform ClassLoader), so both the application code and the HikariCP implementation can see it. The application code only holds references to the interface, never the concrete `HikariDataSource` class.

## Reflection Usage

### Why Reflection?

We use reflection because:
1. **No compile-time dependency** on HikariCP classes
2. **Classes loaded in different classloader** than calling code
3. **Clean separation** between application and isolated driver environment

### Safety

✅ **Type Safe**: Cast to standard `DataSource` interface  
✅ **Method Safe**: Using well-known HikariCP API methods  
✅ **Version Safe**: HikariCP API is stable across versions  
✅ **Error Handling**: Wrapped in try/catch with clear error messages  

## Testing Impact

**Test code** may still have HikariCP as a test-scoped dependency for convenience, but production code does not depend on it at all.

## Migration Notes

This change is **transparent** to:
- ✅ Tool implementations (still get `ConnectionContext`)
- ✅ Configuration (same config.json format)
- ✅ Users (same behavior, better isolation)

No migration needed - existing configurations work unchanged.

## Performance

**No performance impact**:
- Reflection only used during **pool creation** (once per connection config)
- Connection acquisition uses standard `DataSource.getConnection()` - no reflection
- Connection usage is identical to before

## Future Enhancements

This architecture enables:
1. **Per-driver HikariCP versions** - Different drivers could use different HikariCP versions if needed
2. **Alternative pools** - Could support other connection pools (C3P0, DBCP) by classloader
3. **Hot reload** - Can reload driver+pool without restarting application
4. **Memory isolation** - Each driver/pool combo completely isolated

---

*"Indirection is the key to everything in computer science."* - David Wheeler

In this case: The indirection of the `DataSource` interface is the key to crossing classloader boundaries cleanly.

