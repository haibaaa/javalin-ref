# Documentation: TruckHandler.java

## Executive Summary

`TruckHandler.java` implements HTTP request handlers for truck fleet management.
It provides endpoints for listing trucks, retrieving truck details, creating new
trucks, and updating truck status. The handler manages the complete truck
lifecycle from registration through active service to return cycles.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-16**

**Purpose:** Define the handler with its service dependency.

**Technical Breakdown:**
- **Dependency:** `TruckService truckService` - Truck operations
- **Constructor Injection:** Dependency provided via constructor

**Field:**
| Field | Type | Purpose |
|-------|------|---------|
| `truckService` | TruckService | Truck CRUD and status operations |

---

### 2. Get All Trucks Handler
**Lines 18-23**

**Purpose:** Retrieve list of trucks with optional filtering.

**Method Signature:**
```java
public void getAll(Context ctx)
```

**Request Format:**
```
GET /api/trucks?[filters]
Authorization: Bearer <token>
```

**Query Parameters:**

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `status` | String | Filter by status (Available, Allocated, Loading, InTransit) | All |
| `destination` | String | Filter by destination (partial match) | All |

**Example Request:**
```
GET /api/trucks?status=Available
```

**Logic Flow:**
```
1. Extract query parameters from request
2. Call truckService.getAll() with filters
3. Return truck list wrapped in response
```

**Success Response (200):**
```json
{
  "data": {
    "trucks": [
      {
        "truckId": "truck-uuid",
        "registrationNumber": "MH-12-AB-1234",
        "capacity": 500.0,
        "driverName": "John Driver",
        "driverLicense": "DL-123456",
        "status": "Available",
        "currentLocation": "Mumbai Depot",
        "destination": null,
        "cargoVolume": 0.0,
        "updatedAt": "2024-01-01T10:00:00Z"
      }
    ]
  }
}
```

---

### 3. Get Available Trucks Handler
**Lines 25-28**

**Purpose:** Retrieve only trucks with "Available" status.

**Method Signature:**
```java
public void getAvailable(Context ctx)
```

**Request Format:**
```
GET /api/trucks/available
Authorization: Bearer <token>
```

**Logic Flow:**
```
1. Call truckService.getAvailable()
2. Return available trucks list
```

**Success Response (200):**
```json
{
  "data": {
    "trucks": [
      {
        "truckId": "truck-uuid",
        "registrationNumber": "MH-12-AB-1234",
        "capacity": 500.0,
        "driverName": "John Driver",
        "status": "Available"
      }
    ]
  }
}
```

**Use Case:** Quick lookup for allocation decisions without filtering

---

### 4. Get Truck By ID Handler
**Lines 30-42**

**Purpose:** Retrieve detailed information about a specific truck.

**Method Signature:**
```java
public void getById(Context ctx)
```

**Request Format:**
```
GET /api/trucks/{id}
Authorization: Bearer <token>
```

**Path Parameters:**
- `id` - Truck UUID

**Logic Flow:**
```
1. Parse truck ID from path parameter as UUID
2. Call truckService.getById()
3. If found:
   a. Retrieve associated consignments
   b. Retrieve associated dispatches
   c. Return truck with related data
4. If not found → Return 404
```

**Success Response (200):**
```json
{
  "data": {
    "truck": {
      "truckId": "truck-uuid",
      "registrationNumber": "MH-12-AB-1234",
      "capacity": 500.0,
      "driverName": "John Driver",
      "driverLicense": "DL-123456",
      "status": "InTransit",
      "currentLocation": "Highway XYZ",
      "destination": "Mumbai",
      "cargoVolume": 450.5
    },
    "consignments": [
      {
        "consignmentNumber": "TCCS-20240101-0001",
        "volume": 150.5,
        "destination": "Mumbai",
        "status": "InTransit"
      }
    ],
    "dispatches": [
      {
        "dispatchId": "dispatch-uuid",
        "dispatchTimestamp": "2024-01-01T08:00:00Z",
        "dispatchStatus": "InTransit"
      }
    ]
  }
}
```

**Error Response (404):**
```json
{
  "error": "Truck not found"
}
```

---

### 5. Create Truck Handler
**Lines 44-58**

**Purpose:** Register a new truck in the fleet.

**Method Signature:**
```java
public void create(Context ctx) throws Exception
```

**Request Format:**
```json
POST /api/trucks
Content-Type: application/json
Authorization: Bearer <token>

{
  "registrationNumber": "MH-12-AB-1234",
  "capacity": 500.0,
  "driverName": "John Driver",
  "driverLicense": "DL-123456",
  "currentLocation": "Mumbai Depot"
}
```

**Required Fields:**
- `registrationNumber` - Vehicle registration number (must be unique)
- `capacity` - Cargo capacity in cubic meters
- `driverName` - Primary driver's name
- `driverLicense` - Driver's license number

**Optional Fields:**
- `currentLocation` - Current location (defaults to empty string)

**Logic Flow:**
```
1. Extract and parse request body fields
2. Validate registration number uniqueness
3. Call truckService.create()
4. Return created truck
```

**Validation:**
- **Unique Registration:** Throws exception if registration number exists

**Success Response (201):**
```json
{
  "data": {
    "truck": {
      "truckId": "new-truck-uuid",
      "registrationNumber": "MH-12-AB-1234",
      "capacity": 500.0,
      "driverName": "John Driver",
      "driverLicense": "DL-123456",
      "status": "Available",
      "currentLocation": "Mumbai Depot",
      "cargoVolume": 0.0
    }
  }
}
```

**Error Responses:**

| Status | Condition | Response |
|--------|-----------|----------|
| 400 | Duplicate registration | `{"error": "Truck with this registration number already exists"}` |
| 400 | Invalid capacity | `{"error": "error message"}` |
| 401 | Unauthorized | `{"error": "Unauthorized"}` |
| 403 | Insufficient role | `{"error": "Forbidden"}` |

---

### 6. Update Truck Status Handler
**Lines 60-74**

**Purpose:** Update truck status with location and destination tracking.

**Method Signature:**
```java
public void updateStatus(Context ctx) throws Exception
```

**Request Format:**
```json
PATCH /api/trucks/{id}/status
Content-Type: application/json
Authorization: Bearer <token>

{
  "status": "InTransit",
  "note": "Departed for Mumbai",
  "currentLocation": "Highway XYZ Km 45",
  "destination": "Mumbai"
}
```

**Required Fields:**
- `status` - New status value

**Optional Fields:**
- `note` - Reason for status change (for audit log)
- `currentLocation` - Current GPS location or landmark
- `destination` - Target destination

**Valid Status Values:**
- `Available` - Ready for allocation
- `Allocated` - Assigned to cargo, awaiting loading
- `Loading` - Being loaded at depot
- `InTransit` - En route to destination

**Logic Flow:**
```
1. Parse truck ID from path parameter
2. Extract status, note, location, destination from body
3. Get current user from request context
4. Call truckService.updateStatus()
5. Return updated truck
```

**Success Response (200):**
```json
{
  "data": {
    "truck": {
      "truckId": "truck-uuid",
      "registrationNumber": "MH-12-AB-1234",
      "status": "InTransit",
      "currentLocation": "Highway XYZ Km 45",
      "destination": "Mumbai",
      "cargoVolume": 450.5
    }
  }
}
```

**Error Response (404):**
```json
{
  "error": "Truck not found"
}
```

**Audit Trail:**
Each status change is logged in `status_history` JSON column with:
- `status` - New status
- `timestamp` - When change occurred
- `note` - Reason for change
- `updatedBy` - User who made the change

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `TruckService` | Truck business logic | [TruckService.java](../service/TruckService.java.md) |
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |
| `UsersRecord` | User record type | Database schema |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context
- **java.math.BigDecimal** - Precise decimal handling for capacity
- **java.util.UUID** - UUID parsing

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("trucks", () -> {
    get(truckHandler::getAll, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
    get("available", truckHandler::getAvailable, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
    post(truckHandler::create, Role.TransportManager, Role.SystemAdministrator);
    path("{id}", () -> {
        get(truckHandler::getById, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
        patch("status", truckHandler::updateStatus, Role.TransportManager, Role.SystemAdministrator);
    });
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/trucks` | GET | BranchOperator, TransportManager, SystemAdministrator | List trucks |
| `/api/trucks/available` | GET | BranchOperator, TransportManager, SystemAdministrator | List available trucks |
| `/api/trucks` | POST | TransportManager, SystemAdministrator | Register new truck |
| `/api/trucks/{id}` | GET | BranchOperator, TransportManager, SystemAdministrator | Get truck details |
| `/api/trucks/{id}/status` | PATCH | TransportManager, SystemAdministrator | Update truck status |

---

## Truck Status Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    Truck Status Lifecycle                    │
└─────────────────────────────────────────────────────────────┘

    ┌─────────────┐
    │  Available  │ ←── Created or returned from delivery
    └──────┬──────┘
           │
           │ Allocation triggered
           │
     ┌─────▼──────┐
     │ Allocated  │ ←── Assigned to destination
     └─────┬──────┘
           │
           │ Loading begins
           │
    ┌──────▼───────────┐
    │    Loading       │ ←── Being loaded at depot
    └──────┬───────────┘
           │
           │ Dispatch created
           │
    ┌──────▼───────────┐
    │   InTransit      │ ←── En route to destination
    └──────┬───────────┘
           │
           │ Returns empty/after delivery
           │
    ┌──────▼───────────┐
    │   Available      │ ←── Ready for next allocation
    └──────────────────┘
```

---

## Integration Points

### AllocationService
[AllocationService](../service/AllocationService.java.md) updates truck during allocation:
```java
truck.setStatus("Allocated");
truck.setDestination(destination);
truck.setCargoVolume(totalVolume);
```

### DispatchService
[DispatchService](../service/DispatchService.java.md) updates truck during dispatch:
```java
truckService.updateStatus(truckId, "InTransit", "Dispatch generated", ...);
```

### ConsignmentHandler
[ConsignmentHandler](ConsignmentHandler.java.md) retrieves truck info:
```java
var truckInfo = truckService.getById(c.getAssignedTruckId());
```

### DispatchHandler
[DispatchHandler](DispatchHandler.java.md) retrieves truck for dispatch details:
```java
var truck = truckService.getById(d.getTruckId());
```

---

## Business Rules

### Registration Number Uniqueness
- Each truck must have a unique registration number
- Duplicate registration attempts are rejected
- Format should follow local vehicle registration standards

### Status Transitions
- Available → Allocated (via allocation)
- Allocated → Loading (manual)
- Loading → InTransit (via dispatch)
- InTransit → Available (on return)

### Capacity Management
- Capacity is measured in cubic meters (m³)
- Cargo volume tracks current load
- Cargo volume resets to 0 when truck returns

---

## Testing Guidelines

### List Trucks
```bash
curl -X GET "http://localhost:8080/api/trucks?status=Available" \
  -H "Authorization: Bearer $TOKEN"
```

### Get Available Trucks
```bash
curl -X GET http://localhost:8080/api/trucks/available \
  -H "Authorization: Bearer $TOKEN"
```

### Get Truck Details
```bash
curl -X GET http://localhost:8080/api/trucks/truck-uuid-here \
  -H "Authorization: Bearer $TOKEN"
```

### Create Truck
```bash
curl -X POST http://localhost:8080/api/trucks \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "registrationNumber": "MH-12-AB-1234",
    "capacity": 500.0,
    "driverName": "John Driver",
    "driverLicense": "DL-123456",
    "currentLocation": "Mumbai Depot"
  }'
```

### Update Truck Status
```bash
curl -X PATCH http://localhost:8080/api/trucks/truck-uuid-here/status \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "InTransit",
    "note": "Departed for Mumbai",
    "currentLocation": "Highway XYZ Km 45",
    "destination": "Mumbai"
  }'
```
