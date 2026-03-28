# Documentation: TruckService.java

## Executive Summary

`TruckService.java` manages the truck fleet lifecycle including registration,
status tracking, location monitoring, and relationship queries. It handles
truck CRUD operations, maintains status history with audit logs, tracks cargo
assignments, and automatically updates related consignments and dispatches
when trucks complete their routes.

---

## Logical Block Analysis

### 1. Class Structure & Dependencies
**Lines 8-22**

**Purpose:** Define the service with required dependencies.

**Technical Breakdown:**
- **Dependencies:**
  - `DSLContext dsl` - Database operations via JOOQ
  - `ObjectMapper mapper` - JSON serialization for status history

**Key Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database query execution |
| `mapper` | ObjectMapper | JSON history serialization |

---

### 2. Get All Trucks Method
**Lines 24-35**

**Purpose:** Retrieve trucks with optional filtering.

**Method Signature:**
```java
public List<TrucksRecord> getAll(String status, String destination)
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | String | Filter by status (exact match) |
| `destination` | String | Filter by destination (partial match) |

**Query Building:**
```java
var query = dsl.selectFrom(TRUCKS);
if (status != null) {
    query.where(TRUCKS.STATUS.eq(status));
}
if (destination != null) {
    query.where(TRUCKS.DESTINATION.containsIgnoreCase(destination));
}
return query.orderBy(TRUCKS.UPDATED_AT.desc()).fetch();
```

**Returns:** List of `TrucksRecord` sorted by last update time descending

---

### 3. Get Available Trucks Method
**Lines 37-42**

**Purpose:** Retrieve only trucks with "Available" status.

**Method Signature:**
```java
public List<TrucksRecord> getAvailable()
```

**Database Query:**
```sql
SELECT * FROM trucks
WHERE status = 'Available'
```

**Returns:** List of available trucks ready for allocation

**Usage:**
- Allocation algorithm truck selection
- Dashboard availability display
- Quick capacity check

---

### 4. Get By ID Method
**Lines 44-47**

**Purpose:** Retrieve a single truck by UUID.

**Method Signature:**
```java
public TrucksRecord getById(UUID id)
```

**Database Query:**
```sql
SELECT * FROM trucks
WHERE truck_id = ?
```

**Returns:** `TrucksRecord` or null if not found

---

### 5. Get Consignments By Truck ID Method
**Lines 49-52**

**Purpose:** Retrieve all consignments assigned to a truck.

**Method Signature:**
```java
public List<ConsignmentsRecord> getConsignmentsByTruckId(UUID truckId)
```

**Database Query:**
```sql
SELECT * FROM consignments
WHERE assigned_truck_id = ?
```

**Returns:** List of all consignments (current and historical) for the truck

---

### 6. Get Dispatches By Truck ID Method
**Lines 54-59**

**Purpose:** Retrieve dispatch documents for a truck.

**Method Signature:**
```java
public List<DispatchDocumentsRecord> getDispatchesByTruckId(UUID truckId)
```

**Database Query:**
```sql
SELECT * FROM dispatch_documents
WHERE truck_id = ?
ORDER BY dispatch_timestamp DESC
```

**Returns:** List of dispatch documents sorted by timestamp descending

---

### 7. Create Truck Method
**Lines 61-79**

**Purpose:** Register a new truck in the fleet.

**Method Signature:**
```java
public TrucksRecord create(String reg, BigDecimal capacity, String driverName, 
    String driverLicense, String currentLocation) throws Exception
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `reg` | String | Registration number |
| `capacity` | BigDecimal | Cargo capacity (m³) |
| `driverName` | String | Primary driver name |
| `driverLicense` | String | Driver's license number |
| `currentLocation` | String | Current location |

**Logic Flow:**

**Step 1: Validate Uniqueness (Lines 63-66)**
```java
if (dsl.fetchExists(TRUCKS, TRUCKS.REGISTRATION_NUMBER.eq(reg))) {
    throw new IllegalArgumentException("Truck with this registration number already exists");
}
```

**Step 2: Initialize Status History (Lines 68-71)**
```java
List<Map<String, Object>> history = List.of(Map.of(
    "status", "Available",
    "timestamp", OffsetDateTime.now().toString(),
    "note", "Truck registered"
));
```

**Step 3: Create Record (Lines 73-80)**
```java
TrucksRecord truck = dsl.newRecord(TRUCKS);
truck.setRegistrationNumber(reg);
truck.setCapacity(capacity);
truck.setDriverName(driverName);
truck.setDriverLicense(driverLicense);
truck.setStatus("Available");
truck.setCurrentLocation(currentLocation);
truck.setCargoVolume(BigDecimal.ZERO);
truck.setStatusHistory(JSON.json(mapper.writeValueAsString(history)));
truck.store();
return truck;
```

---

### 8. Update Status Method
**Lines 81-130**

**Purpose:** Update truck status with location tracking and automatic related record updates.

**Method Signature:**
```java
public TrucksRecord updateStatus(UUID id, String newStatus, String note, 
    String currentLocation, String destination, UsersRecord user) throws Exception
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Truck ID |
| `newStatus` | String | New status value |
| `note` | String | Reason for status change |
| `currentLocation` | String | Current location (optional) |
| `destination` | String | Target destination (optional) |
| `user` | UsersRecord | Updating user (for audit) |

**Logic Flow:**

**Step 1: Retrieve Truck (Lines 83-85)**
```java
TrucksRecord truck = getById(id);
if (truck == null) return null;
```

**Step 2: Capture Old Status (Lines 87-88)**
```java
String oldStatus = truck.getStatus();
boolean wasInTransit = "InTransit".equals(oldStatus);
```

**Step 3: Parse Existing History (Lines 90-91)**
```java
List<Map<String, Object>> history = mapper.readValue(
    truck.getStatusHistory().data(), 
    new TypeReference<>() {}
);
```

**Step 4: Create History Entry (Lines 92-97)**
```java
Map<String, Object> entry = new LinkedHashMap<>();
entry.put("status", newStatus);
entry.put("timestamp", OffsetDateTime.now().toString());
entry.put("note", note);
if (user != null) entry.put("updatedBy", user.getName());
history.add(entry);
```

**Step 5: Update Truck Fields (Lines 99-107)**
```java
truck.setStatus(newStatus);
truck.setStatusHistory(JSON.json(mapper.writeValueAsString(history)));

if (currentLocation != null) truck.setCurrentLocation(currentLocation);
if (destination != null) truck.setDestination(destination);

if ("Available".equals(newStatus)) {
    truck.setCargoVolume(BigDecimal.ZERO);
    truck.setDestination(null);
}

truck.store();
```

**Step 6: Handle Return from Transit (Lines 109-125)**
```java
if ("Available".equals(newStatus) && wasInTransit) {
    // Mark consignments as delivered
    dsl.update(CONSIGNMENTS)
        .set(CONSIGNMENTS.STATUS, "Delivered")
        .where(CONSIGNMENTS.ASSIGNED_TRUCK_ID.eq(id))
        .and(CONSIGNMENTS.STATUS.eq("InTransit"))
        .execute();
    
    // Mark dispatches as delivered
    dsl.update(DISPATCH_DOCUMENTS)
        .set(DISPATCH_DOCUMENTS.DISPATCH_STATUS, "Delivered")
        .set(DISPATCH_DOCUMENTS.ARRIVAL_TIME, OffsetDateTime.now())
        .where(DISPATCH_DOCUMENTS.TRUCK_ID.eq(id))
        .and(DISPATCH_DOCUMENTS.DISPATCH_STATUS.eq("InTransit"))
        .execute();
}
```

**Step 7: Return Updated Truck (Line 127)**
```java
return truck;
```

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `TrucksRecord` | JOOQ truck record | Database schema |
| `ConsignmentsRecord` | JOOQ consignment record | Database schema |
| `DispatchDocumentsRecord` | JOOQ dispatch record | Database schema |
| `UsersRecord` | User record | Database schema |
| `TruckHandler` | HTTP endpoint | [TruckHandler.java](../handler/TruckHandler.java.md) |
| `AllocationService` | Uses trucks for allocation | [AllocationService.java](AllocationService.java.md) |
| `DispatchService` | Updates truck status | [DispatchService.java](DispatchService.java.md) |

### External Dependencies

- **org.jooq.DSLContext** - Database query interface
- **org.jooq.JSON** - JSON type handling
- **com.fasterxml.jackson.databind.ObjectMapper** - JSON serialization
- **com.fasterxml.jackson.core.type.TypeReference** - Type-safe JSON parsing
- **java.math.BigDecimal** - Precise volume/capacity handling
- **java.time.OffsetDateTime** - Timestamp handling

---

## Truck Status Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                    Truck Status Lifecycle                    │
└─────────────────────────────────────────────────────────────┘

    ┌─────────────┐
    │  Available  │ ←── TruckService.create()
    └──────┬──────┘  ←── TruckService.updateStatus("Available") [return]
           │
           │ AllocationService.checkAndTriggerAllocation()
           │
     ┌─────▼──────┐
     │ Allocated  │ ←── Assigned to destination with cargo
     └─────┬──────┘
           │
           │ Manual status update
           │
    ┌──────▼───────────┐
    │    Loading       │ ←── Being loaded at depot
    └──────┬───────────┘
           │
           │ DispatchService.create()
           │
    ┌──────▼───────────┐
    │   InTransit      │ ←── En route to destination
    └──────┬───────────┘
           │
           │ TruckService.updateStatus("Available")
           │ [Automatic: marks consignments/dispatches as Delivered]
           │
    ┌──────▼───────────┐
    │   Available      │ ←── Ready for next allocation
    └──────────────────┘
```

---

## Status History Structure

### JSON Format
```json
[
  {
    "status": "Available",
    "timestamp": "2024-01-01T00:00:00Z",
    "note": "Truck registered"
  },
  {
    "status": "Allocated",
    "timestamp": "2024-01-01T10:00:00Z",
    "note": "Allocated for Mumbai with 5 consignments (520.50m³)"
  },
  {
    "status": "InTransit",
    "timestamp": "2024-01-01T14:00:00Z",
    "note": "Dispatch generated",
    "updatedBy": "Transport Manager"
  },
  {
    "status": "Available",
    "timestamp": "2024-01-02T08:00:00Z",
    "note": "Returned from Mumbai",
    "updatedBy": "Branch Operator"
  }
]
```

### History Entry Fields

| Field | Type | Description |
|-------|------|-------------|
| `status` | String | New status value |
| `timestamp` | String | ISO 8601 timestamp |
| `note` | String | Reason for change |
| `updatedBy` | String | User name (optional) |

---

## Integration Points

### AllocationService
[AllocationService](AllocationService.java.md) - Truck selection and status update:
```java
truck.setStatus("Allocated");
truck.setDestination(destination);
truck.setCargoVolume(totalVolume);
truck.store();
```

### DispatchService
[DispatchService](DispatchService.java.md) - Status update on dispatch:
```java
truckService.updateStatus(truckId, "InTransit", "Dispatch generated", null, null, user);
```

### TruckHandler
[TruckHandler](../handler/TruckHandler.java.md) - HTTP endpoints:
```java
var truck = truckService.updateStatus(id, newStatus, note, currentLocation, destination, user);
```

### ConsignmentService
[ConsignmentService](ConsignmentService.java.md) - Automatic delivery on truck return:
```java
// In TruckService.updateStatus()
dsl.update(CONSIGNMENTS)
   .set(CONSIGNMENTS.STATUS, "Delivered")
   .where(CONSIGNMENTS.ASSIGNED_TRUCK_ID.eq(id))
   .execute();
```

---

## Business Rules

### Registration Number Uniqueness
- Each truck must have unique registration number
- Validated before creation
- Format follows local vehicle registration standards

### Status Transitions

| From | To | Trigger |
|------|-----|---------|
| (new) | Available | create() |
| Available | Allocated | AllocationService |
| Allocated | Loading | Manual update |
| Loading | InTransit | DispatchService |
| InTransit | Available | updateStatus() [auto-delivers] |

### Cargo Volume Management
- Reset to 0 when truck returns (status → Available)
- Set to total volume during allocation
- Tracks current load during transit

### Destination Tracking
- Set during allocation
- Cleared when truck returns
- Used for filtering and reporting

### Audit Requirements
- All status changes logged in status_history
- Logs include timestamp, status, note
- User attribution for manual changes
- History stored as JSON in database

---

## Automatic Delivery Processing

When a truck returns from transit (status changes from "InTransit" to "Available"):

### Consignment Updates
```java
dsl.update(CONSIGNMENTS)
    .set(CONSIGNMENTS.STATUS, "Delivered")
    .where(CONSIGNMENTS.ASSIGNED_TRUCK_ID.eq(id))
    .and(CONSIGNMENTS.STATUS.eq("InTransit"))
    .execute();
```

**Effect:**
- All in-transit consignments marked as "Delivered"
- Bulk update (no individual status logs)
- Efficient batch operation

### Dispatch Updates
```java
dsl.update(DISPATCH_DOCUMENTS)
    .set(DISPATCH_DOCUMENTS.DISPATCH_STATUS, "Delivered")
    .set(DISPATCH_DOCUMENTS.ARRIVAL_TIME, OffsetDateTime.now())
    .where(DISPATCH_DOCUMENTS.TRUCK_ID.eq(id))
    .and(DISPATCH_DOCUMENTS.DISPATCH_STATUS.eq("InTransit"))
    .execute();
```

**Effect:**
- All in-transit dispatches marked as "Delivered"
- Arrival time recorded
- Completes dispatch lifecycle

---

## Error Handling

### Duplicate Registration
```java
throw new IllegalArgumentException("Truck with this registration number already exists");
```
- HTTP 400 in handler
- Prevents duplicate entries

### Truck Not Found
```java
TrucksRecord truck = getById(id);
if (truck == null) return null;
```
- Returns null (silent fail)
- Handler returns 404

### JSON Serialization Errors
- Caught in history append operations
- Prevents status update failure due to logging

### Database Errors
- JOOQ exceptions propagate
- Transaction rollback on failure

---

## Performance Considerations

### Query Optimization
- Index on `trucks(status)` for filtering
- Index on `trucks(registration_number)` for uniqueness check
- Index on `trucks(destination)` for filtering
- Index on `consignments(assigned_truck_id)` for delivery updates
- Index on `dispatch_documents(truck_id)` for delivery updates

### Batch Updates
- Delivery processing uses bulk UPDATE
- Single query updates all consignments
- Efficient for trucks with many consignments

### History Size
- Status history grows with each change
- Consider archiving old entries
- Typical truck: 50-100 status changes per year

---

## Testing Guidelines

### Create Truck
```java
TrucksRecord truck = truckService.create(
    "MH-12-AB-1234",
    new BigDecimal("500.0"),
    "John Driver",
    "DL-123456",
    "Mumbai Depot"
);

assert truck.getTruckId() != null;
assert truck.getStatus().equals("Available");
assert truck.getCargoVolume().compareTo(BigDecimal.ZERO) == 0;
```

### Update Status
```java
TrucksRecord updated = truckService.updateStatus(
    truckId,
    "InTransit",
    "Departed for Mumbai",
    "Highway XYZ Km 45",
    "Mumbai",
    user
);

assert updated.getStatus().equals("InTransit");
assert updated.getDestination().equals("Mumbai");
```

### Get Available Trucks
```java
List<TrucksRecord> available = truckService.getAvailable();
assert available.stream()
    .allMatch(t -> t.getStatus().equals("Available"));
```

### Test Automatic Delivery
```java
// Setup: Truck in InTransit status with consignments
truckService.updateStatus(truckId, "Available", "Returned", null, null, user);

// Verify consignments delivered
var consignments = dsl.selectFrom(CONSIGNMENTS)
    .where(CONSIGNMENTS.ASSIGNED_TRUCK_ID.eq(truckId))
    .fetch();
assert consignments.stream()
    .allMatch(c -> c.getStatus().equals("Delivered"));

// Verify dispatches delivered
var dispatches = dsl.selectFrom(DISPATCH_DOCUMENTS)
    .where(DISPATCH_DOCUMENTS.TRUCK_ID.eq(truckId))
    .fetch();
assert dispatches.stream()
    .allMatch(d -> d.getDispatchStatus().equals("Delivered"));
```
