# Documentation: ConsignmentHandler.java

## Executive Summary

`ConsignmentHandler.java` implements HTTP request handlers for consignment
management operations. It provides endpoints for listing, creating, retrieving,
and updating consignments. The handler coordinates between `ConsignmentService`
for consignment operations and `TruckService` for truck information, managing
the complete consignment lifecycle from registration to delivery.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-20**

**Purpose:** Define the handler with required service dependencies.

**Technical Breakdown:**
- **Dependencies:**
  - `ConsignmentService consignmentService` - Consignment operations
  - `TruckService truckService` - Truck lookup for assignment info
- **Constructor Injection:** Both dependencies provided via constructor

**Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `consignmentService` | ConsignmentService | Consignment CRUD operations |
| `truckService` | TruckService | Truck information retrieval |

---

### 2. Get All Consignments Handler
**Lines 22-40**

**Purpose:** Retrieve paginated list of consignments with filtering options.

**Method Signature:**
```java
public void getAll(Context ctx)
```

**Request Format:**
```
GET /api/consignments?[filters]
Authorization: Bearer <token>
```

**Query Parameters:**

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `status` | String | Filter by status (Registered, Pending, AllocatedToTruck, InTransit, Delivered, Cancelled) | All |
| `destination` | String | Filter by destination (partial match) | All |
| `search` | String | Search in consignment number, sender/receiver addresses | None |
| `startDate` | Date | Filter by registration date (ISO format) | None |
| `endDate` | Date | Filter by registration date (ISO format) | None |
| `limit` | Integer | Maximum results to return | 50 |
| `offset` | Integer | Pagination offset | 0 |

**Example Request:**
```
GET /api/consignments?status=Registered&destination=Mumbai&limit=20&offset=0
```

**Logic Flow:**
```
1. Extract all query parameters from request
2. Call consignmentService.getAll() with filters
3. Call consignmentService.countAll() for total count
4. Return paginated response with list and metadata
```

**Success Response (200):**
```json
{
  "data": {
    "consignments": [
      {
        "consignmentNumber": "TCCS-20240101-0001",
        "volume": 150.5,
        "destination": "Mumbai",
        "senderAddress": "123 Sender St",
        "receiverAddress": "456 Receiver Ave",
        "status": "Registered",
        "registrationTimestamp": "2024-01-01T10:00:00Z",
        "transportCharges": 752.50
      }
    ],
    "total": 125,
    "limit": 50,
    "offset": 0
  }
}
```

**Response Fields:**
- `consignments` - Array of consignment records
- `total` - Total count matching filters (for pagination)
- `limit` - Applied limit
- `offset` - Applied offset

---

### 3. Create Consignment Handler
**Lines 42-59**

**Purpose:** Create a new consignment with automatic billing and allocation.

**Method Signature:**
```java
public void create(Context ctx) throws Exception
```

**Request Format:**
```json
POST /api/consignments
Content-Type: application/json
Authorization: Bearer <token>

{
  "volume": 150.5,
  "destination": "Mumbai",
  "senderAddress": "123 Sender St, Delhi",
  "receiverAddress": "456 Receiver Ave, Mumbai"
}
```

**Required Fields:**
- `volume` - Cargo volume in cubic meters (BigDecimal)
- `destination` - Destination city name
- `senderAddress` - Pickup address
- `receiverAddress` - Delivery address

**Logic Flow:**
```
1. Extract and parse request body fields
2. Get current user from request context
3. Call consignmentService.create() which:
   a. Generates consignment number
   b. Creates consignment record
   c. Generates bill via BillService
   d. Triggers allocation check
4. Return comprehensive response with all details
```

**Success Response (201):**
```json
{
  "data": {
    "consignment": {
      "consignmentNumber": "TCCS-20240101-0001",
      "volume": 150.5,
      "destination": "Mumbai",
      "status": "Registered",
      "transportCharges": 752.50
    },
    "bill": {
      "billId": "uuid",
      "consignmentNumber": "TCCS-20240101-0001",
      "transportCharges": 752.50
    },
    "pricingBreakdown": {
      "volume": 150.5,
      "ratePerCubicMeter": 5.00,
      "minimumCharge": 100.00,
      "baseCharge": 752.50,
      "finalCharge": 752.50,
      "appliedRule": "rate"
    },
    "allocationTriggered": true,
    "allocationDetails": {
      "triggered": false,
      "reason": "Volume 150.50m³ < 500m³ threshold",
      "totalVolume": 150.5
    }
  }
}
```

**Error Responses:**

| Status | Condition | Response |
|--------|-----------|----------|
| 400 | Invalid volume format | `{"error": "error message"}` |
| 401 | Unauthorized | `{"error": "Unauthorized"}` |
| 403 | Insufficient role | `{"error": "Forbidden"}` |
| 500 | No pricing rule | `{"error": "No active pricing rule..."}` |

---

### 4. Get Consignment By ID Handler
**Lines 61-72**

**Purpose:** Retrieve detailed information about a specific consignment.

**Method Signature:**
```java
public void getById(Context ctx)
```

**Request Format:**
```
GET /api/consignments/{id}
Authorization: Bearer <token>
```

**Path Parameters:**
- `id` - Consignment number (e.g., `TCCS-20240101-0001`)

**Logic Flow:**
```
1. Extract consignment ID from path parameter
2. Call consignmentService.getById()
3. If found:
   a. Retrieve assigned truck info (if any)
   b. Return consignment with truck details
4. If not found → Return 404
```

**Success Response (200):**
```json
{
  "data": {
    "consignment": {
      "consignmentNumber": "TCCS-20240101-0001",
      "volume": 150.5,
      "destination": "Mumbai",
      "status": "AllocatedToTruck",
      "assignedTruckId": "truck-uuid"
    },
    "truck": {
      "truckId": "truck-uuid",
      "registrationNumber": "MH-12-AB-1234",
      "driverName": "John Driver",
      "capacity": 500.0
    }
  }
}
```

**Error Response (404):**
```json
{
  "error": "Consignment not found"
}
```

---

### 5. Update Status Handler
**Lines 74-82**

**Purpose:** Update the status of a consignment with audit logging.

**Method Signature:**
```java
public void updateStatus(Context ctx) throws Exception
```

**Request Format:**
```json
PATCH /api/consignments/{id}/status
Content-Type: application/json
Authorization: Bearer <token>

{
  "status": "InTransit",
  "note": "Loaded onto truck MH-12-AB-1234"
}
```

**Required Fields:**
- `status` - New status value

**Optional Fields:**
- `note` - Reason for status change (defaults to "Status updated to {status}")

**Valid Status Values:**
- `Registered` - Initial status
- `Pending` - Awaiting allocation
- `AllocatedToTruck` - Assigned to truck
- `InTransit` - On the way to destination
- `Delivered` - Successfully delivered
- `Cancelled` - Consignment cancelled

**Logic Flow:**
```
1. Extract consignment ID from path
2. Extract new status and note from body
3. Get current user from request context
4. Call consignmentService.updateStatus()
5. Return success confirmation
```

**Success Response (200):**
```json
{
  "data": {
    "message": "Status updated"
  }
}
```

**Audit Trail:**
Each status change is logged in the `status_change_log` JSON column with:
- `oldStatus` - Previous status
- `newStatus` - New status
- `timestamp` - When change occurred
- `note` - Reason for change
- `updatedBy` - User who made the change (if authenticated)

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `ConsignmentService` | Consignment business logic | [ConsignmentService.java](../service/ConsignmentService.java.md) |
| `TruckService` | Truck information | [TruckService.java](../service/TruckService.java.md) |
| `BillService` | Billing operations | [BillService.java](../service/BillService.java.md) |
| `AllocationService` | Allocation logic | [AllocationService.java](../service/AllocationService.java.md) |
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |
| `UsersRecord` | User record type | Database schema |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context
- **java.math.BigDecimal** - Precise decimal handling for volume

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("consignments", () -> {
    get(consignmentHandler::getAll, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
    post(consignmentHandler::create, Role.BranchOperator, Role.SystemAdministrator);
    path("{id}", () -> {
        get(consignmentHandler::getById, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
        patch("status", consignmentHandler::updateStatus, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
    });
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/consignments` | GET | BranchOperator, TransportManager, SystemAdministrator | List consignments |
| `/api/consignments` | POST | BranchOperator, SystemAdministrator | Create consignment |
| `/api/consignments/{id}` | GET | BranchOperator, TransportManager, SystemAdministrator | Get consignment details |
| `/api/consignments/{id}/status` | PATCH | BranchOperator, TransportManager, SystemAdministrator | Update status |

---

## Consignment Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│              Consignment Status Lifecycle                    │
└─────────────────────────────────────────────────────────────┘

    ┌─────────────┐
    │  Registered │ ←── Created via POST /api/consignments
    └──────┬──────┘
           │
           │ Auto-allocation check
           │
     ┌─────▼──────┐
     │  Pending   │ ←── Volume below threshold
     └─────┬──────┘
           │
           │ Allocation triggered
           │
    ┌──────▼───────────┐
    │ AllocatedToTruck │ ←── Assigned to truck
    └──────┬───────────┘
           │
           │ Dispatch created
           │
    ┌──────▼───────────┐
    │   InTransit      │ ←── Truck departed
    └──────┬───────────┘
           │
           │ Truck arrives
           │
    ┌──────▼───────────┐
    │   Delivered      │ ←── Final state
    └──────────────────┘

    Alternative paths:
    - Registered → Cancelled
    - Pending → Cancelled
    - AllocatedToTruck → Cancelled (requires admin)
```

---

## Integration Points

### BillService
Automatic billing on consignment creation:
```java
var billResult = billService.generateBill(consignmentNumber, volume, destination, now);
```

### AllocationService
Automatic allocation check on creation:
```java
var allocationResult = allocationService.checkAndTriggerAllocation(destination);
```

### TruckService
Truck lookup for consignment details:
```java
var truckInfo = truckService.getById(c.getAssignedTruckId());
```

### DashboardHandler
Consignments displayed in dashboard:
```java
var recentConsignments = dsl.selectFrom(CONSIGNMENTS)
    .orderBy(CONSIGNMENTS.REGISTRATION_TIMESTAMP.desc())
    .limit(5)
    .fetch();
```

---

## Consignment Number Format

Consignments are assigned sequential numbers daily:

**Format:** `TCCS-YYYYMMDD-NNNN`

**Examples:**
- `TCCS-20240101-0001` - First consignment on Jan 1, 2024
- `TCCS-20240101-0042` - 42nd consignment on Jan 1, 2024
- `TCCS-20240102-0001` - First consignment on Jan 2, 2024

The counter resets daily, ensuring unique, sortable identifiers.

---

## Testing Guidelines

### List Consignments
```bash
curl -X GET "http://localhost:8080/api/consignments?status=Registered&limit=20" \
  -H "Authorization: Bearer $TOKEN"
```

### Create Consignment
```bash
curl -X POST http://localhost:8080/api/consignments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "volume": 150.5,
    "destination": "Mumbai",
    "senderAddress": "123 Sender St, Delhi",
    "receiverAddress": "456 Receiver Ave, Mumbai"
  }'
```

### Get Consignment Details
```bash
curl -X GET http://localhost:8080/api/consignments/TCCS-20240101-0001 \
  -H "Authorization: Bearer $TOKEN"
```

### Update Status
```bash
curl -X PATCH http://localhost:8080/api/consignments/TCCS-20240101-0001/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "InTransit", "note": "Dispatched via truck MH-12-AB-1234"}'
```
