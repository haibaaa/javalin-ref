# Documentation: DispatchHandler.java

## Executive Summary

`DispatchHandler.java` implements HTTP request handlers for dispatch document
management. It provides endpoints for listing dispatches, creating new dispatch
documents (which triggers truck dispatch), and retrieving dispatch details.
The handler coordinates between `DispatchService` for dispatch operations and
`TruckService` for truck information.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-18**

**Purpose:** Define the handler with required service dependencies.

**Technical Breakdown:**
- **Dependencies:**
  - `DispatchService dispatchService` - Dispatch operations
  - `TruckService truckService` - Truck lookup for dispatch details
- **Constructor Injection:** Both dependencies provided via constructor

**Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `dispatchService` | DispatchService | Dispatch CRUD operations |
| `truckService` | TruckService | Truck information retrieval |

---

### 2. Get All Dispatches Handler
**Lines 20-27**

**Purpose:** Retrieve list of dispatches with optional filtering.

**Method Signature:**
```java
public void getAll(Context ctx)
```

**Request Format:**
```
GET /api/dispatch?[filters]
Authorization: Bearer <token>
```

**Query Parameters:**

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `status` | String | Filter by dispatch status | All |
| `destination` | String | Filter by destination (partial match) | All |

**Example Request:**
```
GET /api/dispatch?status=Dispatched&destination=Mumbai
```

**Logic Flow:**
```
1. Extract query parameters from request
2. Call dispatchService.getAll() with filters
3. Return dispatch list wrapped in response
```

**Success Response (200):**
```json
{
  "data": {
    "dispatches": [
      {
        "dispatchId": "uuid",
        "truckId": "truck-uuid",
        "destination": "Mumbai",
        "dispatchTimestamp": "2024-01-01T08:00:00Z",
        "totalConsignments": 5,
        "totalVolume": 520.5,
        "driverName": "John Driver",
        "departureTime": "2024-01-01T06:00:00Z",
        "dispatchStatus": "Dispatched"
      }
    ]
  }
}
```

---

### 3. Create Dispatch Handler
**Lines 29-40**

**Purpose:** Create a new dispatch document and initiate truck dispatch.

**Method Signature:**
```java
public void create(Context ctx) throws Exception
```

**Request Format:**
```json
POST /api/dispatch
Content-Type: application/json
Authorization: Bearer <token>

{
  "truckId": "truck-uuid",
  "departureTime": "2024-01-01T06:00:00Z"
}
```

**Required Fields:**
- `truckId` - UUID of the truck to dispatch

**Optional Fields:**
- `departureTime` - Scheduled/actual departure time (defaults to now)

**Logic Flow:**
```
1. Extract truckId and departureTime from request body
2. Parse truckId as UUID
3. Parse departureTime as OffsetDateTime (if provided)
4. Get current user from request context
5. Call dispatchService.create() which:
   a. Validates truck exists and is in correct status
   b. Finds all allocated consignments for the truck
   c. Creates dispatch document with manifest
   d. Updates truck status to InTransit
   e. Updates consignment statuses to InTransit
6. Return created dispatch
```

**Preconditions:**
- Truck must exist
- Truck status must be "Allocated" or "Loading"
- Truck must have at least one allocated consignment

**Success Response (201):**
```json
{
  "data": {
    "dispatch": {
      "dispatchId": "dispatch-uuid",
      "truckId": "truck-uuid",
      "destination": "Mumbai",
      "dispatchTimestamp": "2024-01-01T08:00:00Z",
      "totalConsignments": 5,
      "totalVolume": 520.5,
      "driverName": "John Driver",
      "departureTime": "2024-01-01T06:00:00Z",
      "dispatchStatus": "Dispatched",
      "consignmentManifest": [
        {
          "consignmentNumber": "TCCS-20240101-0001",
          "volume": 150.5,
          "senderAddress": "...",
          "receiverAddress": "...",
          "charges": 752.50
        }
      ]
    }
  }
}
```

**Error Responses:**

| Status | Condition | Response |
|--------|-----------|----------|
| 400 | Invalid UUID format | `{"error": "error message"}` |
| 401 | Unauthorized | `{"error": "Unauthorized"}` |
| 403 | Insufficient role | `{"error": "Forbidden"}` |
| 404 | Truck not found | `{"error": "Truck not found"}` |
| 409 | Invalid truck status | `{"error": "Truck must be in Allocated or Loading status"}` |
| 409 | No consignments | `{"error": "No consignments allocated to this truck"}` |

---

### 4. Get Dispatch By ID Handler
**Lines 42-52**

**Purpose:** Retrieve detailed information about a specific dispatch.

**Method Signature:**
```java
public void getById(Context ctx)
```

**Request Format:**
```
GET /api/dispatch/{id}
Authorization: Bearer <token>
```

**Path Parameters:**
- `id` - Dispatch UUID

**Logic Flow:**
```
1. Parse dispatch ID from path parameter as UUID
2. Call dispatchService.getById()
3. If found:
   a. Retrieve truck details
   b. Return dispatch with truck info
4. If not found → Return 404
```

**Success Response (200):**
```json
{
  "data": {
    "dispatch": {
      "dispatchId": "dispatch-uuid",
      "truckId": "truck-uuid",
      "destination": "Mumbai",
      "dispatchTimestamp": "2024-01-01T08:00:00Z",
      "totalConsignments": 5,
      "totalVolume": 520.5,
      "driverName": "John Driver",
      "dispatchStatus": "InTransit"
    },
    "truck": {
      "truckId": "truck-uuid",
      "registrationNumber": "MH-12-AB-1234",
      "driverName": "John Driver",
      "capacity": 500.0,
      "status": "InTransit",
      "currentLocation": "Highway XYZ"
    }
  }
}
```

**Error Response (404):**
```json
{
  "error": "Dispatch not found"
}
```

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `DispatchService` | Dispatch business logic | [DispatchService.java](../service/DispatchService.java.md) |
| `TruckService` | Truck information | [TruckService.java](../service/TruckService.java.md) |
| `ConsignmentService` | Consignment updates | [ConsignmentService.java](../service/ConsignmentService.java.md) |
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |
| `UsersRecord` | User record type | Database schema |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context
- **java.time.OffsetDateTime** - Timestamp handling
- **java.util.UUID** - UUID parsing

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("dispatch", () -> {
    get(dispatchHandler::getAll, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
    post(dispatchHandler::create, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
    path("{id}", () -> {
        get(dispatchHandler::getById, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
    });
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/dispatch` | GET | BranchOperator, TransportManager, SystemAdministrator | List dispatches |
| `/api/dispatch` | POST | BranchOperator, TransportManager, SystemAdministrator | Create dispatch |
| `/api/dispatch/{id}` | GET | BranchOperator, TransportManager, SystemAdministrator | Get dispatch details |

---

## Dispatch Document Structure

### Dispatch Document Fields

| Field | Type | Description |
|-------|------|-------------|
| `dispatchId` | UUID | Unique dispatch identifier |
| `truckId` | UUID | Reference to dispatched truck |
| `destination` | String | Target destination |
| `dispatchTimestamp` | OffsetDateTime | When dispatch was created |
| `totalConsignments` | Integer | Number of consignments |
| `totalVolume` | BigDecimal | Total cargo volume |
| `driverName` | String | Driver name (copied from truck) |
| `departureTime` | OffsetDateTime | Actual departure time |
| `dispatchStatus` | String | Dispatch status |
| `consignmentManifest` | JSON | Array of consignment details |
| `createdBy` | UUID | User who created dispatch |
| `arrivalTime` | OffsetDateTime | When truck arrived (set on return) |

### Consignment Manifest Entry

Each entry in the manifest contains:
- `consignmentNumber` - Consignment identifier
- `volume` - Cargo volume
- `senderAddress` - Pickup location
- `receiverAddress` - Delivery location
- `charges` - Transport charges

---

## Dispatch Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                    Dispatch Creation Flow                    │
└─────────────────────────────────────────────────────────────┘

1. Prerequisites:
   - Truck status = "Allocated" or "Loading"
   - At least one consignment allocated to truck

2. Create Dispatch:
   POST /api/dispatch {truckId, departureTime}
           │
           ▼
   DispatchService.create()
           │
     ┌─────┴─────┐
     │           │
     ▼           ▼
┌─────────┐ ┌─────────────┐
│ Validate│ │ Get allocated│
│ truck   │ │ consignments│
└────┬────┘ └──────┬──────┘
     │             │
     └──────┬──────┘
            │
            ▼
   Create dispatch document
   with manifest
            │
      ┌─────┴─────┐
      │           │
      ▼           ▼
┌─────────────┐ ┌─────────────┐
│ Update truck│ │ Update      │
│ to InTransit│ │ consignments│
│             │ │ to InTransit│
└─────────────┘ └─────────────┘
            │
            ▼
   Return dispatch document
```

---

## Integration Points

### DispatchService
Core dispatch operations:
```java
var dispatch = dispatchService.create(truckId, departureTime, user);
```

### TruckService
Truck information and status updates:
```java
TrucksRecord truck = truckService.getById(truckId);
truckService.updateStatus(truckId, "InTransit", ...);
```

### ConsignmentService
Consignment status updates:
```java
consignmentService.updateStatus(consignmentNumber, "InTransit", ...);
```

---

## Business Rules

### Truck Status Requirements
- Only trucks in "Allocated" or "Loading" status can be dispatched
- After dispatch, truck status becomes "InTransit"
- Truck must have at least one allocated consignment

### Consignment Status Updates
- All allocated consignments are automatically marked "InTransit"
- Consignment manifest is snapshot at dispatch time

### Dispatch Document Immutability
- Once created, dispatch documents should not be modified
- Status changes (e.g., arrival) update the dispatch record

---

## Testing Guidelines

### List Dispatches
```bash
curl -X GET "http://localhost:8080/api/dispatch?status=Dispatched" \
  -H "Authorization: Bearer $TOKEN"
```

### Create Dispatch
```bash
curl -X POST http://localhost:8080/api/dispatch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "truckId": "truck-uuid-here",
    "departureTime": "2024-01-01T06:00:00Z"
  }'
```

### Get Dispatch Details
```bash
curl -X GET http://localhost:8080/api/dispatch/dispatch-uuid-here \
  -H "Authorization: Bearer $TOKEN"
```
