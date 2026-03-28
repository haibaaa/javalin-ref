# Documentation: ApiResponse.java

## Executive Summary

`ApiResponse.java` defines a generic response wrapper used consistently across all
API endpoints. It provides a standardized structure for both successful responses
(containing data) and error responses (containing error messages), ensuring uniform
API client experience throughout the application.

---

## Logical Block Analysis

### 1. Class Structure & Annotations
**Lines 1-6**

**Purpose:** Define the generic response container with JSON serialization hints.

**Technical Breakdown:**
- **Generic Type `<T>`:** Allows the response to wrap any data type
- **`@JsonInclude(JsonInclude.Include.NON_NULL)`:** Jackson annotation that
  excludes `null` fields from JSON output, reducing response payload size

**Fields:**
- `data` (T) - Holds the successful response payload
- `error` (String) - Holds error message when operation fails

**Design Principle:** Mutually exclusive fields - typically only one is populated
per response.

---

### 2. Constructors
**Lines 8-13**

**Purpose:** Provide object instantiation methods.

**Constructor Breakdown:**

| Constructor | Parameters | Usage |
|-------------|------------|-------|
| No-arg | None | Default constructor for Jackson deserialization |
| Parameterized | `T data`, `String error` | Manual instantiation with both fields |

---

### 3. Accessor Methods
**Lines 15-20**

**Purpose:** Standard JavaBean getters and setters for field access.

**Methods:**
- `getData()` / `setData(T data)` - Access and modify data payload
- `getError()` / `setError(String error)` - Access and modify error message

---

### 4. Static Factory Methods
**Lines 22-28**

**Purpose:** Provide convenient, semantic methods for creating responses.

**Method Breakdown:**

#### `ok(T data)` - Success Response
```java
public static <T> ApiResponse<T> ok(T data)
```
- Returns response with `data` populated and `error` set to `null`
- Used for successful operations

#### `error(String message)` - Error Response
```java
public static <T> ApiResponse<T> error(String message)
```
- Returns response with `error` populated and `data` set to `null`
- Used for failed operations and exceptions

**Usage Examples:**
```java
// Success response
ctx.json(ApiResponse.ok(Map.of("users", userList)));

// Error response
ctx.status(404).json(ApiResponse.error("User not found"));
```

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| None | Standalone DTO | - |

### External Dependencies

- **com.fasterxml.jackson.annotation.JsonInclude** - JSON serialization control
- **com.fasterxml.jackson.annotation.JsonInclude.Include** - Inclusion strategy enum

---

## JSON Response Examples

### Success Response
```json
{
  "data": {
    "token": "abc123",
    "user": {
      "userId": "uuid-here",
      "username": "john.doe"
    }
  }
}
```

### Error Response
```json
{
  "error": "Invalid username or password"
}
```

### Empty Success Response
```json
{
  "data": "OK"
}
```

**Note:** Due to `@JsonInclude(NON_NULL)`, the `null` field is omitted from
the JSON output, keeping responses clean and minimal.

---

## Integration Points

### Used in Main.java
All API endpoints in [Main.java](../Main.java.md) use `ApiResponse` for consistent
response formatting:

```java
get("health", ctx -> ctx.json(ApiResponse.ok("OK")), Role.ANYONE);
```

### Used in Handlers
All handler classes use `ApiResponse`:

- [AuthHandler](handler/AuthHandler.java.md) - Login/logout responses
- [TruckHandler](handler/TruckHandler.java.md) - Truck CRUD responses
- [ConsignmentHandler](handler/ConsignmentHandler.java.md) - Consignment responses
- [DispatchHandler](handler/DispatchHandler.java.md) - Dispatch responses
- [AllocationHandler](handler/AllocationHandler.java.md) - Allocation responses
- [PricingHandler](handler/PricingHandler.java.md) - Pricing responses
- [DashboardHandler](handler/DashboardHandler.java.md) - Dashboard stats
- [ReportHandler](handler/ReportHandler.java.md) - Report data
- [UserHandler](handler/UserHandler.java.md) - User listing

---

## Design Patterns

**Generic Wrapper Pattern:** Provides a type-safe wrapper that can encapsulate
any response type while maintaining a consistent API structure.

**Static Factory Pattern:** The `ok()` and `error()` methods provide semantic,
readable ways to create responses without exposing constructor implementation
details.

**Null Object Pattern:** By excluding null fields from JSON, the response
structure adapts to the context (success vs error) without requiring clients
to handle missing fields.

---

## Best Practices

1. **Always use factory methods** - Prefer `ApiResponse.ok(data)` and
   `ApiResponse.error(message)` over direct constructor calls

2. **Set appropriate HTTP status codes** - Combine `ApiResponse` with
   appropriate HTTP status codes (200, 201, 400, 401, 403, 404, 500)

3. **Keep error messages user-friendly** - Error messages should be clear
   and actionable for API consumers
