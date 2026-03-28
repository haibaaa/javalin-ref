# Documentation: Main.java

## Executive Summary

`Main.java` serves as the application entry point for the TCCS (Transport Consignment
Management System). It orchestrates the initialization of all core components including
the database connection pool, dependency injection container, services, handlers, and
the Javalin web server with complete routing configuration.

---

## Logical Block Analysis

### 1. Application Bootstrap & Configuration
**Lines 1-36**

**Purpose:** Initialize the application configuration, database connection, and JSON
serializer.

**Technical Breakdown:**
- Creates `AppConfig` instance to load application properties
- Calls `createDataSource()` to configure HikariCP connection pool for PostgreSQL
- Initializes JOOQ `DSLContext` for type-safe SQL operations
- Configures Jackson `ObjectMapper` to disable timestamp serialization for dates

**Key Variables:**
- `config` - Application configuration holder
- `dataSource` - HikariCP managed database connection pool
- `dsl` - JOOQ database context
- `om` - Jackson ObjectMapper for JSON serialization

---

### 2. Service Layer Initialization
**Lines 38-47**

**Purpose:** Instantiate all service classes with their required dependencies.

**Technical Breakdown:**
Services are created in dependency order:
1. `SecurityService` - Requires only DSLContext
2. `PricingService` - Requires only DSLContext
3. `BillService` - Requires DSLContext, ObjectMapper, and PricingService
4. `AllocationService` - Requires DSLContext, ObjectMapper, and AppConfig
5. `TruckService` - Requires DSLContext and ObjectMapper
6. `ConsignmentService` - Requires DSLContext, ObjectMapper, BillService, and
   AllocationService
7. `DispatchService` - Requires DSLContext, ObjectMapper, TruckService, and
   ConsignmentService

**Dependency Graph:**
```
SecurityService ← (none)
PricingService  ← (none)
BillService     ← PricingService
AllocationService ← (none)
TruckService    ← (none)
ConsignmentService ← BillService, AllocationService
DispatchService ← TruckService, ConsignmentService
```

---

### 3. Handler Layer Initialization
**Lines 49-59**

**Purpose:** Instantiate all HTTP request handlers (controllers) with their required
service dependencies.

**Handler-Service Mapping:**
- `AuthHandler` → SecurityService
- `TruckHandler` → TruckService
- `ConsignmentHandler` → ConsignmentService, TruckService
- `DispatchHandler` → DispatchService, TruckService
- `PricingHandler` → PricingService
- `DashboardHandler` → DSLContext, AllocationService
- `ReportHandler` → DSLContext
- `UserHandler` → DSLContext
- `AllocationHandler` → AllocationService

---

### 4. Javalin Server & Routing Configuration
**Lines 61-111**

**Purpose:** Configure and start the Javalin web server with complete API routing,
CORS settings, and JSON mapper.

**Technical Breakdown:**
The routing table is organized under `/api` base path:

| Endpoint | Method | Handler | Required Roles |
|----------|--------|---------|----------------|
| `/auth/login` | POST | AuthHandler::login | ANYONE |
| `/auth/logout` | POST | AuthHandler::logout | ANYONE |
| `/auth/me` | GET | AuthHandler::me | BranchOperator, TransportManager, SystemAdministrator |
| `/trucks` | GET | TruckHandler::getAll | BranchOperator, TransportManager, SystemAdministrator |
| `/trucks/available` | GET | TruckHandler::getAvailable | BranchOperator, TransportManager, SystemAdministrator |
| `/trucks` | POST | TruckHandler::create | TransportManager, SystemAdministrator |
| `/trucks/{id}` | GET | TruckHandler::getById | BranchOperator, TransportManager, SystemAdministrator |
| `/trucks/{id}/status` | PATCH | TruckHandler::updateStatus | TransportManager, SystemAdministrator |
| `/allocation/trigger` | POST | AllocationHandler::trigger | TransportManager, SystemAdministrator |
| `/allocation/pending-volumes` | GET | AllocationHandler::getPendingVolumes | TransportManager, SystemAdministrator |
| `/consignments` | GET | ConsignmentHandler::getAll | BranchOperator, TransportManager, SystemAdministrator |
| `/consignments` | POST | ConsignmentHandler::create | BranchOperator, SystemAdministrator |
| `/consignments/{id}` | GET | ConsignmentHandler::getById | BranchOperator, TransportManager, SystemAdministrator |
| `/consignments/{id}/status` | PATCH | ConsignmentHandler::updateStatus | BranchOperator, TransportManager, SystemAdministrator |
| `/dispatch` | GET | DispatchHandler::getAll | BranchOperator, TransportManager, SystemAdministrator |
| `/dispatch` | POST | DispatchHandler::create | BranchOperator, TransportManager, SystemAdministrator |
| `/dispatch/{id}` | GET | DispatchHandler::getById | BranchOperator, TransportManager, SystemAdministrator |
| `/pricing` | GET | PricingHandler::getAll | BranchOperator, TransportManager, SystemAdministrator |
| `/pricing` | POST | PricingHandler::create | SystemAdministrator |
| `/dashboard/stats` | GET | DashboardHandler::getStats | BranchOperator, TransportManager, SystemAdministrator |
| `/reports/revenue` | GET | ReportHandler::revenue | TransportManager, SystemAdministrator |
| `/reports/export/csv` | GET | ReportHandler::exportCsv | TransportManager, SystemAdministrator |
| `/users` | GET | UserHandler::getAll | SystemAdministrator |
| `/health` | GET | ApiResponse::ok | ANYONE |

**CORS Configuration:**
- Configured via `bundledPlugins.enableCors()`
- Allowed origins read from `tccs.cors.allowed-origins` config property
- Defaults to `http://localhost:5173` (typical Vite dev server)

---

### 5. Security Filter (Before Handler)
**Lines 113-133**

**Purpose:** Implement authentication and authorization checks before route execution.

**Logic Flow:**
```
1. Retrieve permitted roles for the matched route
2. If roles empty or contains ANYONE → allow request (skip filter)
3. Extract Authorization header (must be "Bearer <token>")
4. Validate token via SecurityService.getUser()
5. Check if user's role matches one of the permitted roles
6. If authorized → store user in request context as "currentUser"
7. If unauthorized → return 401 or 403 with error response
```

**Key Security Checks:**
- **401 Unauthorized:** Missing/invalid Authorization header or invalid token
- **403 Forbidden:** Valid token but insufficient role privileges

---

### 6. Exception Handling & Server Start
**Lines 135-142**

**Purpose:** Configure global exception handler and start the server.

**Technical Breakdown:**
- Catches all `Exception` instances
- Logs stack trace to console
- Returns 500 status with `ApiResponse.error()` containing exception message
- Server starts on port from config (default: 8080)

---

### 7. DataSource Factory Method
**Lines 144-152**

**Purpose:** Configure and return a HikariCP DataSource for PostgreSQL.

**Configuration Parameters:**
- `db.url` - JDBC connection URL
- `db.username` - Database username
- `db.password` - Database password
- `db.pool.max` - Maximum pool size (default: 10)

**Returns:** `HikariDataSource` instance managing the connection pool

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `AppConfig` | Load application properties | [AppConfig.java](AppConfig.java.md) |
| `ApiResponse` | Standard API response wrapper | [ApiResponse.java](dto/ApiResponse.java.md) |
| `Role` | Enum for route-based authorization | [Role.java](security/Role.java.md) |
| `SecurityService` | Authentication and session management | [SecurityService.java](security/SecurityService.java.md) |
| `PricingService` | Pricing rule management | [PricingService.java](service/PricingService.java.md) |
| `BillService` | Generate bills and calculate charges | [BillService.java](service/BillService.java.md) |
| `AllocationService` | Auto-allocate consignments to trucks | [AllocationService.java](service/AllocationService.java.md) |
| `TruckService` | Truck CRUD and status management | [TruckService.java](service/TruckService.java.md) |
| `ConsignmentService` | Consignment lifecycle management | [ConsignmentService.java](service/ConsignmentService.java.md) |
| `DispatchService` | Dispatch document generation | [DispatchService.java](service/DispatchService.java.md) |
| `AuthHandler` | Login/logout endpoints | [AuthHandler.java](handler/AuthHandler.java.md) |
| `TruckHandler` | Truck management endpoints | [TruckHandler.java](handler/TruckHandler.java.md) |
| `ConsignmentHandler` | Consignment management endpoints | [ConsignmentHandler.java](handler/ConsignmentHandler.java.md) |
| `DispatchHandler` | Dispatch management endpoints | [DispatchHandler.java](handler/DispatchHandler.java.md) |
| `PricingHandler` | Pricing rule endpoints | [PricingHandler.java](handler/PricingHandler.java.md) |
| `DashboardHandler` | Dashboard statistics | [DashboardHandler.java](handler/DashboardHandler.java.md) |
| `ReportHandler` | Revenue reports and CSV export | [ReportHandler.java](handler/ReportHandler.java.md) |
| `UserHandler` | User listing endpoint | [UserHandler.java](handler/UserHandler.java.md) |
| `AllocationHandler` | Allocation trigger endpoint | [AllocationHandler.java](handler/AllocationHandler.java.md) |

### External Dependencies

- **Javalin** - Web framework for routing and HTTP handling
- **HikariCP** - High-performance JDBC connection pool
- **JOOQ** - Type-safe SQL query builder
- **Jackson** - JSON serialization/deserialization
- **PostgreSQL Driver** - Database connectivity

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Main.java                               │
│  (Application Bootstrap & Orchestration)                     │
└─────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        v                   v                   v
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│   Handlers    │   │   Services    │   │  DataSource   │
│  (Controllers)│   │ (Business Log)│   │   (HikariCP)  │
└───────────────┘   └───────────────┘   └───────────────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                            v
                   ┌─────────────────┐
                   │   PostgreSQL    │
                   │     Database    │
                   └─────────────────┘
```

---

## Key Design Patterns

**Dependency Injection:** Manual constructor-based DI - services and handlers receive
dependencies via constructor parameters.

**Route-Based Authorization:** Javalin's `RouteRole` system enforces role-based access
control at the routing level.

**Filter Chain:** Security filter runs before matched routes, enabling centralized
authentication/authorization logic.

**Global Exception Handling:** Single exception handler catches all unhandled
exceptions, ensuring consistent error responses.
