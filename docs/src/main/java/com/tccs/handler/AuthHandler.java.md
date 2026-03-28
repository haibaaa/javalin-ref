# Documentation: AuthHandler.java

## Executive Summary

`AuthHandler.java` implements the HTTP request handlers for authentication
endpoints. It provides login, logout, and current user retrieval functionality,
acting as the interface between HTTP requests and the `SecurityService` for
all authentication-related operations.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-14**

**Purpose:** Define the handler with its service dependency.

**Technical Breakdown:**
- **Dependency:** `SecurityService securityService` - Handles authentication logic
- **Constructor Injection:** Dependency provided via constructor

**Field:**
| Field | Type | Purpose |
|-------|------|---------|
| `securityService` | SecurityService | Authentication operations |

---

### 2. Login Handler
**Lines 16-30**

**Purpose:** Process login requests and return authentication token.

**Method Signature:**
```java
public void login(Context ctx)
```

**Request Format:**
```json
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Logic Flow:**
```
1. Extract username and password from request body
2. Call securityService.login(username, password)
3. If token returned (success):
   a. Retrieve user record via securityService.getUser(token)
   b. Return {token, user}
4. If null returned (failure):
   a. Return 401 with error message
```

**Success Response (200):**
```json
{
  "data": {
    "token": "uuid-here",
    "user": {
      "userId": "uuid",
      "username": "admin",
      "role": "SystemAdministrator",
      "name": "Administrator"
    }
  }
}
```

**Error Response (401):**
```json
{
  "error": "Invalid username or password"
}
```

---

### 3. Logout Handler
**Lines 32-39**

**Purpose:** Invalidate user session and terminate authentication.

**Method Signature:**
```java
public void logout(Context ctx)
```

**Request Format:**
```
POST /api/auth/logout
Authorization: Bearer <token>
```

**Logic Flow:**
```
1. Extract Authorization header
2. If header exists and starts with "Bearer ":
   a. Extract token (remove "Bearer " prefix)
   b. Call securityService.logout(token)
3. Return success message
```

**Response (200):**
```json
{
  "data": "Logged out"
}
```

**Note:** Logout always succeeds even if token is invalid (idempotent operation)

---

### 4. Current User Handler (Me)
**Lines 41-49**

**Purpose:** Return the currently authenticated user's information.

**Method Signature:**
```java
public void me(Context ctx)
```

**Request Format:**
```
GET /api/auth/me
Authorization: Bearer <token>
```

**Logic Flow:**
```
1. Retrieve user from request context attribute "currentUser"
   (set by security filter in Main.java)
2. If user exists:
   a. Return user record
3. If null:
   a. Return 401 with error message
```

**Success Response (200):**
```json
{
  "data": {
    "userId": "uuid",
    "username": "admin",
    "role": "SystemAdministrator",
    "name": "Administrator"
  }
}
```

**Error Response (401):**
```json
{
  "error": "Not logged in"
}
```

**Security Note:** This endpoint relies on the security filter in `Main.java`
to validate the token and set the `currentUser` attribute before the handler
executes.

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `SecurityService` | Authentication logic | [SecurityService.java](../security/SecurityService.java.md) |
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |
| `Role` | Authorization enum | [Role.java](../security/Role.java.md) |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("auth", () -> {
    post("login", authHandler::login, Role.ANYONE);
    post("logout", authHandler::logout, Role.ANYONE);
    get("me", authHandler::me, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/auth/login` | POST | ANYONE | Public login endpoint |
| `/api/auth/logout` | POST | ANYONE | Public logout endpoint |
| `/api/auth/me` | GET | Authenticated users only | Get current user info |

---

## Authentication Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Authentication Flow                       │
└─────────────────────────────────────────────────────────────┘

1. LOGIN
   Client ──POST /api/auth/login──→ AuthHandler.login()
                                      │
                                      ▼
                              SecurityService.login()
                                      │
                                      ▼
                              Return token + user
                                      │
   Client ←── {token, user} ──────────┘

2. AUTHENTICATED REQUEST
   Client ──GET /api/trucks + Bearer token──→ Security Filter
                                               │
                                               ▼
                                       Validate token
                                               │
                                               ▼
                                       Set currentUser attribute
                                               │
                                               ▼
                                       Handler executes

3. GET CURRENT USER
   Client ──GET /api/auth/me──→ AuthHandler.me()
                                 │
                                 ▼
                         Return currentUser attribute

4. LOGOUT
   Client ──POST /api/auth/logout──→ AuthHandler.logout()
                                      │
                                      ▼
                              SecurityService.logout()
                                      │
                                      ▼
                              Remove session
```

---

## Security Considerations

### Token Handling
- Tokens are UUIDs stored in memory (ConcurrentHashMap in SecurityService)
- Tokens must be sent in `Authorization: Bearer <token>` header
- No token encryption; use HTTPS in production

### Session Management
- Sessions are in-memory and lost on application restart
- No automatic session expiration implemented
- Logout removes session from memory

### Error Messages
- Login failures return generic "Invalid username or password"
- Prevents username enumeration attacks

---

## Integration Points

### Main.java Security Filter
The `me()` endpoint depends on the security filter in [Main.java](../Main.java.md):

```java
// Before filter sets currentUser attribute
app.beforeMatched(ctx -> {
    // ... token validation ...
    ctx.attribute("currentUser", user);
});
```

### SecurityService
All authentication operations delegate to [SecurityService](../security/SecurityService.java.md):
- `login()` → Creates session and returns token
- `getUser()` → Retrieves user from session
- `logout()` → Invalidates session

---

## Testing Guidelines

### Login Test
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Authenticated Request Test
```bash
TOKEN="your-token-here"
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

### Logout Test
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```
