# Documentation: AppConfig.java

## Executive Summary

`AppConfig.java` provides a centralized configuration management system for the
application. It loads properties from `config.properties` file and supports
environment variable overrides, enabling flexible deployment configurations
across different environments (development, staging, production).

---

## Logical Block Analysis

### 1. Properties Loading (Constructor)
**Lines 7-15**

**Purpose:** Load application configuration from `config.properties` resource file.

**Technical Breakdown:**
- Uses `Properties` class to store key-value configuration pairs
- Attempts to load from classpath resource `config.properties`
- Silently catches and prints exceptions (graceful degradation)
- Properties are loaded once at application startup

**Key Variables:**
- `props` - Final `Properties` instance holding all configuration values

**Note:** If `config.properties` is missing or unreadable, the application
continues with empty properties, relying on environment variable overrides
or default values in getter methods.

---

### 2. Primary Getter Method
**Lines 17-20**

**Purpose:** Retrieve configuration value with environment variable override support.

**Technical Breakdown:**
- Converts property key to uppercase and replaces dots with underscores
  - Example: `db.url` → `DB_URL`
- Checks environment variable first (takes precedence)
- Falls back to properties file value if env var not set
- Returns `null` if neither source contains the key

**Priority Order:**
```
Environment Variable > config.properties > null
```

---

### 3. Typed Getter Methods with Defaults
**Lines 22-32**

**Purpose:** Provide type-safe configuration access with fallback default values.

**Method Breakdown:**

| Method | Return Type | Purpose |
|--------|-------------|---------|
| `get(String, String)` | String | String value with default fallback |
| `getInt(String, int)` | int | Integer parsing with default fallback |
| `getDouble(String, double)` | double | Double parsing with default fallback |

**Technical Notes:**
- All methods call the primary `get(String)` method first
- Return default value if result is `null`
- No exception handling for parse failures (NumberFormatException propagates)

**Usage Examples:**
```java
config.get("server.port", "8080")           // Returns "8080" if not set
config.getInt("db.pool.max", 10)            // Returns 10 if not set
config.getDouble("tccs.allocation.threshold", 500.0)  // Returns 500.0 if not set
```

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| None | This is a standalone utility class | - |

### External Dependencies

- **java.util.Properties** - Standard Java properties file parser
- **java.io.InputStream** - Resource stream handling

---

## Configuration Keys Reference

### Database Configuration

| Key | Env Variable | Default | Description |
|-----|--------------|---------|-------------|
| `db.url` | `DB_URL` | (required) | PostgreSQL JDBC URL |
| `db.username` | `DB_USERNAME` | (required) | Database username |
| `db.password` | `DB_PASSWORD` | (required) | Database password |
| `db.pool.max` | `DB_POOL_MAX` | 10 | HikariCP max pool size |

### Server Configuration

| Key | Env Variable | Default | Description |
|-----|--------------|---------|-------------|
| `server.port` | `SERVER_PORT` | 8080 | HTTP server port |

### TCCS Business Configuration

| Key | Env Variable | Default | Description |
|-----|--------------|---------|-------------|
| `tccs.allocation.threshold` | `TCCS_ALLOCATION_THRESHOLD` | 500.0 | Volume threshold (m³) for auto-allocation |
| `tccs.cors.allowed-origins` | `TCCS_CORS_ALLOWED_ORIGINS` | http://localhost:5173 | Allowed CORS origins |

---

## Design Patterns

**Environment Override Pattern:** Allows deployment-specific configuration via
environment variables without modifying the properties file. This follows the
12-factor app methodology for configuration management.

**Default Value Pattern:** All typed getters accept default values, preventing
`NullPointerException` and providing sensible fallbacks for optional settings.

---

## Usage Example

```java
// Initialize configuration
AppConfig config = new AppConfig();

// Retrieve values with defaults
String dbUrl = config.get("db.url");  // Required, no default
int port = config.getInt("server.port", 8080);
double threshold = config.getDouble("tccs.allocation.threshold", 500.0);
String corsOrigins = config.get("tccs.cors.allowed-origins", "http://localhost:5173");
```

---

## Security Considerations

- **Sensitive Data:** Database credentials should be provided via environment
  variables in production, not stored in `config.properties`
- **No Encryption:** Configuration values are stored in plain text
- **No Validation:** No validation is performed on configuration values;
  invalid values cause runtime exceptions
