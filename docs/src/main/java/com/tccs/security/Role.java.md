# Documentation: Role.java

## Executive Summary

`Role.java` defines the role-based access control (RBAC) enumeration for the
application. It implements Javalin's `RouteRole` interface, enabling type-safe
route protection and authorization checks. The enum defines four distinct
authorization levels used throughout the API routing configuration.

---

## Logical Block Analysis

### 1. Enum Constants (Role Definitions)
**Lines 6-10**

**Purpose:** Define the authorization levels available in the system.

**Role Hierarchy:**

| Role | Description | Access Level |
|------|-------------|--------------|
| `ANYONE` | Public access, no authentication required | Lowest |
| `BranchOperator` | Operational staff at branch level | Operational |
| `TransportManager` | Manages transport operations and allocations | Managerial |
| `SystemAdministrator` | Full system access including user management | Administrative |

**Role Capabilities:**

```
ANYONE
  └─→ Public endpoints (login, health check)

BranchOperator
  └─→ View trucks, consignments, dispatches
  └─→ Create consignments
  └─→ View dashboard

TransportManager
  └─→ All BranchOperator capabilities
  └─→ Create trucks
  └─→ Trigger allocation
  └─→ Update truck status
  └─→ View reports

SystemAdministrator
  └─→ All TransportManager capabilities
  └─→ Create pricing rules
  └─→ View all users
```

---

### 2. Static Factory Method: `fromString()`
**Lines 12-19**

**Purpose:** Convert string role names (from database or tokens) to enum values.

**Technical Breakdown:**
```java
public static Role fromString(String role) {
    if (role == null) return ANYONE;
    try {
        return Role.valueOf(role);
    } catch (IllegalArgumentException e) {
        return ANYONE;
    }
}
```

**Logic Flow:**
1. If input is `null` → return `ANYONE` (safe default)
2. Attempt to match string to enum constant via `Role.valueOf()`
3. If match fails (invalid role name) → catch exception and return `ANYONE`

**Security Note:** The fallback to `ANYONE` for unknown roles is a safe default
that prevents authorization failures from locking out users, but it means
invalid roles default to no privileges rather than denying access.

**Usage Example:**
```java
// From SecurityService - converting database role to enum
Role userRole = Role.fromString(user.getRole());

// In authorization check
if (!permittedRoles.contains(Role.fromString(user.getRole()))) {
    ctx.status(403).json(ApiResponse.error("Forbidden"));
}
```

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| None | Standalone enum | - |

### External Dependencies

- **io.javalin.http.Context** - HTTP context (imported but not used directly)
- **io.javalin.security.RouteRole** - Marker interface for route roles

---

## Integration Points

### Main.java - Route Protection
[Main.java](../Main.java.md) uses roles extensively in route definitions:

```java
path("auth", () -> {
    post("login", authHandler::login, Role.ANYONE);
    post("logout", authHandler::logout, Role.ANYONE);
    get("me", authHandler::me, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
});
```

### SecurityService.java - Role Validation
[SecurityService.java](SecurityService.java.md) converts user records to roles:

```java
if (!permittedRoles.contains(Role.fromString(user.getRole()))) {
    ctx.status(403).json(ApiResponse.error("Forbidden"));
}
```

---

## Route Role Matrix

### Endpoint Authorization Summary

| Endpoint | ANYONE | BranchOperator | TransportManager | SystemAdministrator |
|----------|--------|----------------|------------------|---------------------|
| POST /api/auth/login | ✓ | ✓ | ✓ | ✓ |
| POST /api/auth/logout | ✓ | ✓ | ✓ | ✓ |
| GET /api/auth/me | ✗ | ✓ | ✓ | ✓ |
| GET /api/trucks | ✗ | ✓ | ✓ | ✓ |
| POST /api/trucks | ✗ | ✗ | ✓ | ✓ |
| PATCH /api/trucks/{id}/status | ✗ | ✗ | ✓ | ✓ |
| POST /api/allocation/trigger | ✗ | ✗ | ✓ | ✓ |
| GET /api/allocation/pending-volumes | ✗ | ✗ | ✓ | ✓ |
| GET /api/consignments | ✗ | ✓ | ✓ | ✓ |
| POST /api/consignments | ✗ | ✓ | ✗ | ✓ |
| PATCH /api/consignments/{id}/status | ✗ | ✓ | ✓ | ✓ |
| GET /api/dispatch | ✗ | ✓ | ✓ | ✓ |
| POST /api/dispatch | ✗ | ✓ | ✓ | ✓ |
| GET /api/pricing | ✗ | ✓ | ✓ | ✓ |
| POST /api/pricing | ✗ | ✗ | ✗ | ✓ |
| GET /api/dashboard/stats | ✗ | ✓ | ✓ | ✓ |
| GET /api/reports/revenue | ✗ | ✗ | ✓ | ✓ |
| GET /api/reports/export/csv | ✗ | ✗ | ✓ | ✓ |
| GET /api/users | ✗ | ✗ | ✗ | ✓ |
| GET /api/health | ✓ | ✓ | ✓ | ✓ |

---

## Design Patterns

**Enum Pattern:** Type-safe representation of fixed set of constants with
compile-time checking.

**RouteRole Interface:** Implements Javalin's marker interface for route-based
authorization, enabling the framework to enforce access control at the routing
level.

**Safe Default Pattern:** The `fromString()` method defaults to `ANYONE` for
unknown inputs, preventing authorization system failures from causing unexpected
access denials.

---

## Security Considerations

1. **Role Names Must Match Database:** The `fromString()` method expects role
   names in the database to exactly match enum constant names (case-sensitive)

2. **No Role Hierarchy Enforcement:** The enum doesn't enforce hierarchy
   (e.g., TransportManager automatically gets BranchOperator permissions);
   this must be manually configured in route definitions

3. **ANYONE as Fallback:** Unknown roles default to `ANYONE`, which could
   inadvertently grant access if role names are misconfigured

---

## Extension Guide

To add a new role:

1. Add enum constant:
   ```java
   Auditor,  // New role
   ```

2. Update route definitions in `Main.java`:
   ```java
   get(endpoint, handler, Role.Auditor);
   ```

3. Update database seed data to include the new role

4. Update this documentation with role capabilities
