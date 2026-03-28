# Documentation: SecurityService.java

## Executive Summary

`SecurityService.java` handles user authentication, password verification, and
session management for the application. It provides login functionality with
token-based session tracking, password hashing using SHA-256, and in-memory
session storage for active user sessions. The service integrates with the
database to validate user credentials.

---

## Logical Block Analysis

### 1. Class Structure & Dependencies
**Lines 1-15**

**Purpose:** Define the service with required dependencies and session storage.

**Technical Breakdown:**
- **Dependency:** `DSLContext dsl` - For database queries via JOOQ
- **Session Storage:** `ConcurrentHashMap<String, UsersRecord>` - Thread-safe
  in-memory session store mapping tokens to user records
- **Database Table:** `USERS` - JOOQ-generated table reference for user queries

**Key Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database query execution |
| `sessions` | ConcurrentHashMap | In-memory session cache (token → user) |

**Thread Safety:** `ConcurrentHashMap` ensures thread-safe session operations
in a multi-request environment.

---

### 2. Login Method
**Lines 17-32**

**Purpose:** Authenticate user credentials and create a session token.

**Technical Breakdown:**
```java
public String login(String username, String password)
```

**Logic Flow:**
```
1. Query database for user by username
2. If user exists:
   a. Verify password against stored hash
   b. If password valid:
      - Generate UUID token
      - Store session (token → user record)
      - Return token
3. Return null if authentication fails
```

**Password Verification Strategy:**
- For seeded BCrypt users (`$2a$` prefix): Accepts hardcoded passwords
  `password123` or `admin123` (development fallback)
- For new users: Uses SHA-256 hash comparison

**Returns:** Session token (UUID string) or `null` on failure

**Usage Example:**
```java
String token = securityService.login("admin", "admin123");
if (token != null) {
    // Authentication successful
}
```

---

### 3. Password Verification (Private)
**Lines 34-43**

**Purpose:** Verify password against stored hash with dual-strategy support.

**Technical Breakdown:**
```java
private boolean verifyPassword(String password, String storedHash)
```

**Verification Logic:**
```
If storedHash starts with "$2a$" (BCrypt):
  → Accept "password123" or "admin123" (seeded user fallback)
Else:
  → Hash input password with SHA-256 and compare
```

**Security Note:** This is a transitional implementation. Production systems
should use BCrypt library for all password hashing.

---

### 4. Password Hashing Method
**Lines 45-54**

**Purpose:** Generate SHA-256 hash for new user passwords.

**Technical Breakdown:**
```java
public String hashPassword(String password)
```

**Algorithm:**
```
1. Get SHA-256 MessageDigest instance
2. Hash password bytes (UTF-8 encoding)
3. Base64 encode the hash
4. Return encoded string
```

**Output Format:** Base64-encoded SHA-256 hash (e.g., `WnZl...==`)

**Usage:** Called when creating new users to store password hashes

---

### 5. Session Retrieval
**Lines 56-59**

**Purpose:** Retrieve user record from session by token.

**Technical Breakdown:**
```java
public UsersRecord getUser(String token)
```

**Returns:** `UsersRecord` if session exists, `null` if invalid/expired

**Integration:** Used by security filter in `Main.java` to validate requests:
```java
var user = securityService.getUser(authHeader.substring(7));
if (user == null) {
    ctx.status(401).json(ApiResponse.error("Invalid token"));
}
```

---

### 6. Logout Method
**Lines 61-64**

**Purpose:** Invalidate session and remove from session store.

**Technical Breakdown:**
```java
public void logout(String token)
```

**Operation:** Simple removal from `sessions` map

**Usage:** Called by `AuthHandler.logout()` to terminate user sessions

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `UsersRecord` | JOOQ-generated user record | Database schema |
| `Role` | Role enumeration | [Role.java](Role.java.md) |

### External Dependencies

- **org.jooq.DSLContext** - Database query interface
- **com.tccs.db.tables.USERS** - JOOQ table reference
- **java.security.MessageDigest** - SHA-256 hashing
- **java.util.Base64** - Hash encoding
- **java.util.concurrent.ConcurrentHashMap** - Thread-safe session storage
- **java.util.UUID** - Token generation

---

## Session Management Architecture

```
┌──────────────┐     Login       ┌──────────────────┐
│   Client     │ ───────────────→│  SecurityService │
│              │                 │                  │
│              │ ←──── Token ─── │  login()         │
│              │                 │  - Verify creds  │
│              │                 │  - Create session│
│              │                 └──────────────────┘
│              │                          │
│              │                          │ stores
│              │                 ┌────────▼────────┐
│              │                 │  sessions Map   │
│              │                 │  token → user   │
│              │                 └─────────────────┘
│              │
│  Request with Token
├──────────────┤
│ Authorization│ ───────────────→ getUser()
│   Filter     │
└──────────────┘
```

---

## Authentication Flow

### Login Sequence
```
Client → POST /api/auth/login {username, password}
   │
   ▼
AuthHandler.login()
   │
   ▼
SecurityService.login(username, password)
   │
   ├─→ Query USERS table
   ├─→ verifyPassword(password, hash)
   ├─→ Generate UUID token
   └─→ sessions.put(token, user)
   │
   ▼
Return {token, user}
```

### Request Authorization
```
Client → GET /api/trucks (Authorization: Bearer <token>)
   │
   ▼
Before Filter (Main.java:113-133)
   │
   ├─→ Extract token from header
   ├─→ securityService.getUser(token)
   ├─→ Validate user role
   └─→ ctx.attribute("currentUser", user)
   │
   ▼
Handler executes with authorized user
```

### Logout Sequence
```
Client → POST /api/auth/logout (Authorization: Bearer <token>)
   │
   ▼
AuthHandler.logout()
   │
   ▼
SecurityService.logout(token)
   │
   └─→ sessions.remove(token)
   │
   ▼
Return "Logged out"
```

---

## Security Considerations

### Current Implementation Limitations

1. **In-Memory Sessions:** Sessions are lost on application restart, requiring
   users to re-login

2. **SHA-256 for Passwords:** SHA-256 is not recommended for password storage;
   BCrypt or Argon2 should be used in production

3. **No Session Expiration:** Sessions don't expire automatically; logout is
   the only invalidation mechanism

4. **No Rate Limiting:** No protection against brute-force login attempts

5. **BCrypt Fallback:** Seeded users with BCrypt hashes use hardcoded password
   matching (development convenience)

### Recommendations for Production

- Implement session expiration (TTL-based cleanup)
- Add BCrypt library dependency for proper password hashing
- Implement login attempt rate limiting
- Consider JWT tokens for stateless authentication
- Add refresh token mechanism for session renewal

---

## Database Schema Reference

### USERS Table (Expected Structure)

| Column | Type | Description |
|--------|------|-------------|
| `user_id` | UUID | Primary key |
| `username` | VARCHAR | Unique username |
| `password_hash` | VARCHAR | SHA-256 or BCrypt hash |
| `role` | VARCHAR | User role (enum name) |
| `name` | VARCHAR | Display name |
| `created_at` | TIMESTAMP | Account creation time |

---

## Usage in Handlers

### AuthHandler
[AuthHandler.java](handler/AuthHandler.java.md) - Direct usage for login/logout:
```java
String token = securityService.login(username, password);
var user = securityService.getUser(token);
securityService.logout(token);
```

### Main.java Security Filter
[Main.java](../Main.java.md) - Session validation:
```java
var user = securityService.getUser(authHeader.substring(7));
if (user == null) {
    ctx.status(401).json(ApiResponse.error("Invalid token"));
}
```
