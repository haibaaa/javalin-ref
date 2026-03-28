# Documentation: UserHandler.java

## Executive Summary

`UserHandler.java` implements a simple HTTP request handler for user management.
Currently, it provides a single endpoint to retrieve all users from the system.
This handler is restricted to System Administrators only, as user data is
sensitive administrative information.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-16**

**Purpose:** Define the handler with database dependency.

**Technical Breakdown:**
- **Dependency:** `DSLContext dsl` - Direct database access
- **Constructor Injection:** Dependency provided via constructor

**Field:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database query execution |

---

### 2. Get All Users Handler
**Lines 18-23**

**Purpose:** Retrieve all user records from the system.

**Method Signature:**
```java
public void getAll(Context ctx)
```

**Request Format:**
```
GET /api/users
Authorization: Bearer <token>
```

**Logic Flow:**
```
1. Execute SELECT * FROM users query via JOOQ
2. Fetch all user records
3. Return users list wrapped in API response
```

**Database Query:**
```sql
SELECT * FROM users
```

**Success Response (200):**
```json
{
  "data": {
    "users": [
      {
        "userId": "user-uuid",
        "username": "admin",
        "name": "Administrator",
        "role": "SystemAdministrator",
        "passwordHash": "[REDACTED]",
        "createdAt": "2024-01-01T00:00:00Z"
      },
      {
        "userId": "user-uuid",
        "username": "operator1",
        "name": "Branch Operator",
        "role": "BranchOperator",
        "passwordHash": "[REDACTED]",
        "createdAt": "2024-01-01T00:00:00Z"
      }
    ]
  }
}
```

**User Record Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `userId` | UUID | Unique user identifier |
| `username` | String | Login username (unique) |
| `name` | String | Display name |
| `role` | String | User role (enum name) |
| `passwordHash` | String | Hashed password (should not be exposed) |
| `createdAt` | Timestamp | Account creation time |

**Security Note:** The `passwordHash` field should ideally be excluded from
the response. Consider selecting only safe fields in production.

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |
| `Role` | Authorization enum | [Role.java](../security/Role.java.md) |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context
- **org.jooq.DSLContext** - Database query interface
- **com.tccs.db.Tables.USERS** - JOOQ table reference

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("users", () -> {
    get(userHandler::getAll, Role.SystemAdministrator);
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/users` | GET | SystemAdministrator only | List all users |

**Security Note:** This endpoint is restricted to System Administrators only
as it exposes user data including role assignments.

---

## Use Cases

### Use Case 1: User Directory
**Scenario:** System Administrator needs to view all system users.

**Steps:**
1. GET `/api/users`
2. Review user list
3. Verify role assignments
4. Audit user accounts

### Use Case 2: Role Audit
**Scenario:** Security audit of role distribution.

**Analysis:**
- Count users by role
- Identify users with elevated privileges
- Verify principle of least privilege

---

## Security Considerations

### Password Hash Exposure
**Current Issue:** The query `SELECT * FROM USERS` includes `passwordHash` in
the response.

**Recommendation:** Select only safe fields:
```java
dsl.select(USERS.USER_ID, USERS.USERNAME, USERS.NAME, USERS.ROLE, USERS.CREATED_AT)
   .from(USERS)
   .fetch()
```

### Access Control
- Only SystemAdministrators can access user list
- Prevents unauthorized enumeration of system users
- Role-based restriction enforced by security filter

### Data Sensitivity
User data includes:
- Username (identifier)
- Role (privilege level)
- Creation timestamp (audit trail)

This information should be protected from unauthorized access.

---

## Integration Points

### SecurityService
[SecurityService](../security/SecurityService.java.md) uses the same USERS table:
```java
UsersRecord user = dsl.selectFrom(USERS)
    .where(USERS.USERNAME.eq(username))
    .fetchOne();
```

### Main.java Security Filter
[Main.java](../Main.java.md) validates user roles:
```java
var user = securityService.getUser(authHeader.substring(7));
if (!permittedRoles.contains(Role.fromString(user.getRole()))) {
    ctx.status(403).json(ApiResponse.error("Forbidden"));
}
```

---

## Future Enhancements

### Potential Additions
Consider adding these endpoints:
- `POST /api/users` - Create new user (SystemAdministrator only)
- `PATCH /api/users/{id}` - Update user details
- `DELETE /api/users/{id}` - Deactivate user account
- `GET /api/users/{id}` - Get single user details
- `PATCH /api/users/{id}/role` - Change user role

### Security Improvements
- Exclude passwordHash from all responses
- Add pagination for large user bases
- Add search/filter capabilities
- Add audit logging for user management actions

---

## Testing Guidelines

### Get All Users
```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Expected Response
```json
{
  "data": {
    "users": [
      {
        "userId": "...",
        "username": "admin",
        "name": "Administrator",
        "role": "SystemAdministrator"
      }
    ]
  }
}
```

### Access Denied Test (Non-Admin User)
```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer $OPERATOR_TOKEN"
```

**Expected Response (403):**
```json
{
  "error": "Forbidden"
}
```

---

## Database Schema Reference

### USERS Table

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `user_id` | UUID | PRIMARY KEY | Unique identifier |
| `username` | VARCHAR | UNIQUE NOT NULL | Login username |
| `name` | VARCHAR | NOT NULL | Display name |
| `password_hash` | VARCHAR | NOT NULL | Password hash |
| `role` | VARCHAR | NOT NULL | User role |
| `created_at` | TIMESTAMP | DEFAULT NOW() | Creation timestamp |

### Seed Data
Typical seed data includes:
- System Administrator account
- Transport Manager account
- Branch Operator account

Each with appropriate role assignments.
