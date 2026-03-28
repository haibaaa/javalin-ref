# Documentation: AllocationHandler.java

## Executive Summary

`AllocationHandler.java` provides HTTP endpoints for the consignment allocation
system. It exposes two endpoints: one to manually trigger the allocation algorithm
for a specific destination, and another to retrieve pending volume statistics across
all destinations. The handler delegates all business logic to `AllocationService`.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-16**

**Purpose:** Define the handler with its service dependency.

**Technical Breakdown:**
- **Dependency:** `AllocationService allocationService` - Handles allocation logic
- **Constructor Injection:** Dependency provided via constructor

**Field:**
| Field | Type | Purpose |
|-------|------|---------|
| `allocationService` | AllocationService | Allocation operations |

---

### 2. Trigger Allocation Handler
**Lines 18-28**

**Purpose:** Manually trigger the allocation algorithm for a specific destination.

**Method Signature:**
```java
public void trigger(Context ctx) throws Exception
```

**Request Format:**
```json
POST /api/allocation/trigger
Content-Type: application/json
Authorization: Bearer <token>

{
  "destination": "Mumbai"
}
```

**Logic Flow:**
```
1. Extract destination from request body
2. Validate destination is present
   - If missing → Return 400 with error
3. Call allocationService.checkAndTriggerAllocation(destination)
4. Return allocation result
```

**Validation:**
- **Required Field:** `destination` - Target destination for allocation

**Success Response (200):**
```json
{
  "data": {
    "triggered": true,
    "reason": "Allocation successful",
    "totalVolume": 520.5,
    "truckInfo": {
      "id": "uuid",
      "registrationNumber": "MH-12-AB-1234",
      "driverName": "John Driver"
    },
    "destination": "Mumbai",
    "consignmentCount": 5,
    "consignments": ["TCCS-20240101-0001", ...],
    "noTrucks": false
  }
}
```

**Error Responses:**

| Status | Condition | Response |
|--------|-----------|----------|
| 400 | Missing destination | `{"error": "Destination is required"}` |
| 401 | Unauthorized | `{"error": "Unauthorized"}` |
| 403 | Insufficient role | `{"error": "Forbidden"}` |
| 500 | Allocation failure | `{"error": "exception message"}` |

**Allocation Result Fields:**
- `triggered` - Boolean indicating if allocation was executed
- `reason` - Human-readable explanation of the outcome
- `totalVolume` - Total volume of consignments considered
- `truckInfo` - Assigned truck details (if allocation succeeded)
- `consignmentCount` - Number of consignments allocated
- `consignments` - List of consignment numbers allocated
- `noTrucks` - True if no available trucks were found

---

### 3. Get Pending Volumes Handler
**Lines 30-33**

**Purpose:** Retrieve pending volume statistics for all destinations.

**Method Signature:**
```java
public void getPendingVolumes(Context ctx)
```

**Request Format:**
```
GET /api/allocation/pending-volumes
Authorization: Bearer <token>
```

**Logic Flow:**
```
1. Call allocationService.getPendingVolumes()
2. Wrap result in Map with key "pendingVolumes"
3. Return as API response
```

**Response (200):**
```json
{
  "data": {
    "pendingVolumes": [
      {
        "destination": "Mumbai",
        "pendingVolume": 450.5,
        "consignmentCount": 4,
        "thresholdPercentage": 90.1,
        "threshold": 500.0,
        "nearingThreshold": true
      },
      {
        "destination": "Delhi",
        "pendingVolume": 120.0,
        "consignmentCount": 2,
        "thresholdPercentage": 24.0,
        "threshold": 500.0,
        "nearingThreshold": false
      }
    ]
  }
}
```

**Response Fields (per destination):**
- `destination` - Target destination name
- `pendingVolume` - Total pending volume in cubic meters
- `consignmentCount` - Number of pending consignments
- `thresholdPercentage` - Percentage of allocation threshold reached
- `threshold` - Configured allocation threshold (default: 500 m³)
- `nearingThreshold` - True if volume ≥ 80% of threshold

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `AllocationService` | Allocation business logic | [AllocationService.java](../service/AllocationService.java.md) |
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |
| `Role` | Authorization enum | [Role.java](../security/Role.java.md) |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("allocation", () -> {
    post("trigger", allocationHandler::trigger, Role.TransportManager, Role.SystemAdministrator);
    get("pending-volumes", allocationHandler::getPendingVolumes, Role.TransportManager, Role.SystemAdministrator);
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/allocation/trigger` | POST | TransportManager, SystemAdministrator | Trigger allocation |
| `/api/allocation/pending-volumes` | GET | TransportManager, SystemAdministrator | View pending volumes |

---

## Allocation System Overview

### How Allocation Works

```
┌─────────────────────────────────────────────────────────────┐
│              Consignment Allocation Process                  │
└─────────────────────────────────────────────────────────────┘

1. Consignments arrive and are marked as "Registered"

2. System checks total pending volume per destination:
   
   Destination: Mumbai
   ├── Consignment A: 100 m³ (Registered)
   ├── Consignment B: 150 m³ (Registered)
   ├── Consignment C: 200 m³ (Pending)
   └── Total: 450 m³ → Below threshold (500 m³)
   
3. When volume ≥ threshold:
   
   a. Find available truck with sufficient capacity
   b. Mark consignments as "AllocatedToTruck"
   c. Update truck status to "Allocated"
   d. Set truck's assigned destination
   e. Record allocation details

4. If no suitable truck:
   - Consignments remain in "Pending" status
   - Manual intervention may be required
```

### Threshold Configuration

The allocation threshold is configurable via `config.properties`:

```properties
tccs.allocation.threshold=500.0
```

Or via environment variable:
```bash
export TCCS_ALLOCATION_THRESHOLD=500.0
```

---

## Use Cases

### Use Case 1: Manual Allocation Trigger
**Scenario:** Transport Manager wants to force allocation for a high-priority
destination before the threshold is reached.

**Steps:**
1. Monitor pending volumes via `/api/allocation/pending-volumes`
2. Identify destination needing allocation
3. POST to `/api/allocation/trigger` with destination
4. Review allocation result

### Use Case 2: Capacity Planning
**Scenario:** Transport Manager needs to assess truck requirements.

**Steps:**
1. GET `/api/allocation/pending-volumes`
2. Review destinations nearing threshold (≥80%)
3. Ensure adequate truck availability
4. Proactively arrange additional capacity if needed

---

## Integration Points

### ConsignmentService
When a consignment is created via [ConsignmentHandler](ConsignmentHandler.java.md),
allocation is automatically triggered:

```java
var allocationResult = allocationService.checkAndTriggerAllocation(destination);
```

### TruckService
Allocation updates truck status and assignment:
- Sets truck status to "Allocated"
- Assigns destination to truck
- Records cargo volume

### DashboardHandler
[DashboardHandler](DashboardHandler.java.md) displays pending volumes:
```java
"pendingVolumes", allocationService.getPendingVolumes()
```

---

## Error Handling

### Validation Errors
- **Missing destination:** Returns 400 Bad Request

### Allocation Failures
- **No available trucks:** Returns success with `triggered: false`, `noTrucks: true`
- **Volume below threshold:** Returns success with `triggered: false`, reason explaining threshold

### Exception Handling
Global exception handler in [Main.java](../Main.java.md) catches and returns:
```json
{
  "error": "exception message"
}
```

---

## Testing Guidelines

### Trigger Allocation
```bash
curl -X POST http://localhost:8080/api/allocation/trigger \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"destination":"Mumbai"}'
```

### Get Pending Volumes
```bash
curl -X GET http://localhost:8080/api/allocation/pending-volumes \
  -H "Authorization: Bearer $TOKEN"
```
