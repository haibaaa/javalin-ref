# Documentation: DispatchService.java

## Executive Summary

`DispatchService.java` manages the creation and tracking of dispatch documents
for trucks departing with allocated consignments. It validates truck readiness,
generates dispatch manifests, updates truck and consignment statuses, and
maintains comprehensive dispatch records for operational tracking and auditing.

---

## Logical Block Analysis

### 1. Class Structure & Dependencies
**Lines 8-24**

**Purpose:** Define the service with required dependencies.

**Technical Breakdown:**
- **Dependencies:**
  - `DSLContext dsl` - Database operations via JOOQ
  - `ObjectMapper mapper` - JSON serialization for manifest
  - `TruckService truckService` - Truck operations
  - `ConsignmentService consignmentService` - Consignment updates

**Key Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database operations |
| `mapper` | ObjectMapper | JSON manifest serialization |
| `truckService` | TruckService | Truck status management |
| `consignmentService` | ConsignmentService | Consignment status updates |

---

### 2. Get All Dispatches Method
**Lines 26-33**

**Purpose:** Retrieve dispatch documents with optional filtering.

**Method Signature:**
```java
public List<DispatchDocumentsRecord> getAll(String status, String destination)
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | String | Filter by dispatch status |
| `destination` | String | Filter by destination (partial match) |

**Query Building:**
```java
var query = dsl.selectFrom(DISPATCH_DOCUMENTS);
if (status != null) 
    query.where(DISPATCH_DOCUMENTS.DISPATCH_STATUS.eq(status));
if (destination != null) 
    query.where(DISPATCH_DOCUMENTS.DESTINATION.containsIgnoreCase(destination));
return query.orderBy(DISPATCH_DOCUMENTS.DISPATCH_TIMESTAMP.desc()).fetch();
```

**Returns:** List of `DispatchDocumentsRecord` sorted by timestamp descending

---

### 3. Create Dispatch Method
**Lines 35-87**

**Purpose:** Create a new dispatch document and initiate truck departure.

**Method Signature:**
```java
public DispatchDocumentsRecord create(UUID truckId, OffsetDateTime departureTime, 
    UsersRecord user) throws Exception
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `truckId` | UUID | Truck to dispatch |
| `departureTime` | OffsetDateTime | Scheduled departure time (optional) |
| `user` | UsersRecord | Creating user (for audit) |

**Logic Flow:**

**Step 1: Validate Truck (Lines 37-42)**
```java
TrucksRecord truck = truckService.getById(truckId);
if (truck == null) 
    throw new RuntimeException("Truck not found");

if (!"Allocated".equals(truck.getStatus()) && !"Loading".equals(truck.getStatus())) {
    throw new RuntimeException("Truck must be in Allocated or Loading status");
}
```

**Step 2: Fetch Allocated Consignments (Lines 44-48)**
```java
var consignments = dsl.selectFrom(CONSIGNMENTS)
    .where(CONSIGNMENTS.ASSIGNED_TRUCK_ID.eq(truckId))
    .and(CONSIGNMENTS.STATUS.eq("AllocatedToTruck"))
    .fetch();

if (consignments.isEmpty()) 
    throw new RuntimeException("No consignments allocated to this truck");
```

**Step 3: Calculate Total Volume (Lines 50-53)**
```java
BigDecimal totalVolume = consignments.stream()
    .map(ConsignmentsRecord::getVolume)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

**Step 4: Build Consignment Manifest (Lines 55-64)**
```java
List<Map<String, Object>> manifest = consignments.stream().map(c -> {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("consignmentNumber", c.getConsignmentNumber());
    item.put("volume", c.getVolume());
    item.put("senderAddress", c.getSenderAddress());
    item.put("receiverAddress", c.getReceiverAddress());
    item.put("charges", c.getTransportCharges());
    return item;
}).collect(Collectors.toList());
```

**Step 5: Create Dispatch Document (Lines 66-77)**
```java
DispatchDocumentsRecord dispatch = dsl.newRecord(DISPATCH_DOCUMENTS);
dispatch.setDispatchId(UUID.randomUUID());
dispatch.setTruckId(truckId);
dispatch.setDestination(truck.getDestination());
dispatch.setDispatchTimestamp(OffsetDateTime.now());
dispatch.setTotalConsignments(consignments.size());
dispatch.setTotalVolume(totalVolume);
dispatch.setDriverName(truck.getDriverName());
dispatch.setDepartureTime(departureTime != null ? departureTime : OffsetDateTime.now());
dispatch.setDispatchStatus("Dispatched");
dispatch.setConsignmentManifest(JSON.json(mapper.writeValueAsString(manifest)));
dispatch.setCreatedBy(user != null ? user.getUserId() : null);
dispatch.store();
```

**Step 6: Update Truck Status (Line 79)**
```java
truckService.updateStatus(truckId, "InTransit", "Dispatch generated", null, null, user);
```

**Step 7: Update Consignment Statuses (Lines 81-84)**
```java
for (ConsignmentsRecord c : consignments) {
    consignmentService.updateStatus(
        c.getConsignmentNumber(), 
        "InTransit", 
        "Truck dispatched - ID: " + dispatch.getDispatchId(), 
        user
    );
}
```

**Step 8: Return Dispatch Document (Line 86)**
```java
return dispatch;
```

---

### 4. Get By ID Method
**Lines 88-91**

**Purpose:** Retrieve a single dispatch document by ID.

**Method Signature:**
```java
public DispatchDocumentsRecord getById(UUID id)
```

**Query:**
```sql
SELECT * FROM dispatch_documents
WHERE dispatch_id = ?
```

**Returns:** `DispatchDocumentsRecord` or null if not found

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `TruckService` | Truck operations | [TruckService.java](TruckService.java.md) |
| `ConsignmentService` | Consignment updates | [ConsignmentService.java](ConsignmentService.java.md) |
| `DispatchDocumentsRecord` | JOOQ dispatch record | Database schema |
| `ConsignmentsRecord` | JOOQ consignment record | Database schema |
| `TrucksRecord` | JOOQ truck record | Database schema |
| `DispatchHandler` | HTTP endpoint | [DispatchHandler.java](../handler/DispatchHandler.java.md) |

### External Dependencies

- **org.jooq.DSLContext** - Database query interface
- **org.jooq.JSON** - JSON type handling
- **com.fasterxml.jackson.databind.ObjectMapper** - JSON serialization
- **java.math.BigDecimal** - Precise volume calculations
- **java.time.OffsetDateTime** - Timestamp handling
- **java.util.stream.Collectors** - Stream operations

---

## Dispatch Document Structure

### DISPATCH_DOCUMENTS Table

| Column | Type | Description |
|--------|------|-------------|
| `dispatch_id` | UUID | Primary key |
| `truck_id` | UUID | Foreign key to trucks |
| `destination` | String | Target destination |
| `dispatch_timestamp` | TIMESTAMP | When dispatch was created |
| `total_consignments` | INTEGER | Number of consignments |
| `total_volume` | DECIMAL | Total cargo volume |
| `driver_name` | VARCHAR | Driver name |
| `departure_time` | TIMESTAMP | Actual departure time |
| `dispatch_status` | VARCHAR | Current status |
| `consignment_manifest` | JSON | Detailed consignment list |
| `created_by` | UUID | Creating user ID |
| `arrival_time` | TIMESTAMP | When truck arrived |

### Consignment Manifest JSON

```json
[
  {
    "consignmentNumber": "TCCS-20240101-0001",
    "volume": 150.5,
    "senderAddress": "123 Sender St, Delhi",
    "receiverAddress": "456 Receiver Ave, Mumbai",
    "charges": 752.50
  },
  {
    "consignmentNumber": "TCCS-20240101-0002",
    "volume": 200.0,
    "senderAddress": "789 Sender Rd, Delhi",
    "receiverAddress": "321 Receiver Blvd, Mumbai",
    "charges": 1000.00
  }
]
```

---

## Dispatch Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                  Dispatch Creation Workflow                  │
└─────────────────────────────────────────────────────────────┘

Prerequisites:
  ✓ Truck status = "Allocated" or "Loading"
  ✓ At least one consignment allocated to truck
  ✓ Truck has assigned destination

1. Validate Truck
   ──────────────────────────────
   GET truck by ID
   IF truck is null → throw "Truck not found"
   IF truck.status not in ["Allocated", "Loading"] 
     → throw "Truck must be in Allocated or Loading status"

2. Fetch Allocated Consignments
   ──────────────────────────────
   SELECT * FROM consignments
   WHERE assigned_truck_id = truckId
     AND status = 'AllocatedToTruck'
   
   IF empty → throw "No consignments allocated to this truck"

3. Calculate Totals
   ──────────────────────────────
   totalVolume = SUM(consignment.volume)
   totalConsignments = COUNT(consignments)

4. Build Manifest
   ──────────────────────────────
   FOR EACH consignment:
     - consignmentNumber
     - volume
     - senderAddress
     - receiverAddress
     - charges

5. Create Dispatch Document
   ──────────────────────────────
   INSERT INTO dispatch_documents
   VALUES (
     dispatch_id = UUID,
     truck_id = truckId,
     destination = truck.destination,
     dispatch_timestamp = NOW(),
     total_consignments = count,
     total_volume = totalVolume,
     driver_name = truck.driverName,
     departure_time = departureTime OR NOW(),
     dispatch_status = 'Dispatched',
     consignment_manifest = manifest_json,
     created_by = user.userId
   )

6. Update Truck Status
   ──────────────────────────────
   truck.status = 'InTransit'
   Append status history log

7. Update Consignment Statuses
   ──────────────────────────────
   FOR EACH consignment:
     consignment.status = 'InTransit'
     note = "Truck dispatched - ID: {dispatchId}"
     Append status change log

8. Return Dispatch Document
```

---

## Integration Points

### TruckService
[TruckService](TruckService.java.md) - Truck validation and status updates:
```java
TrucksRecord truck = truckService.getById(truckId);
truckService.updateStatus(truckId, "InTransit", "Dispatch generated", null, null, user);
```

### ConsignmentService
[ConsignmentService](ConsignmentService.java.md) - Consignment status updates:
```java
consignmentService.updateStatus(
    consignmentNumber, "InTransit", 
    "Truck dispatched - ID: " + dispatch.getDispatchId(), 
    user
);
```

### DispatchHandler
[DispatchHandler](../handler/DispatchHandler.java.md) - HTTP endpoints:
```java
var dispatch = dispatchService.create(truckId, departureTime, user);
```

---

## Business Rules

### Dispatch Prerequisites
- Truck must exist and be in "Allocated" or "Loading" status
- Truck must have at least one allocated consignment
- Truck must have assigned destination and cargo volume

### Status Transitions

**Truck:**
```
Allocated/Loading → InTransit (on dispatch)
```

**Consignments:**
```
AllocatedToTruck → InTransit (on dispatch)
```

**Dispatch:**
```
Dispatched → InTransit → Delivered (on truck return)
```

### Manifest Snapshot
- Manifest captures consignment details at dispatch time
- Changes to consignments after dispatch don't update manifest
- Manifest serves as legal shipping document

### Audit Requirements
- Dispatch creation is audited (created_by)
- Departure time is recorded
- All status changes logged in respective tables

---

## Validation Rules

### Truck Status Validation
```java
if (!"Allocated".equals(truck.getStatus()) && !"Loading".equals(truck.getStatus())) {
    throw new RuntimeException("Truck must be in Allocated or Loading status");
}
```

**Rationale:**
- Only trucks prepared for departure can be dispatched
- Prevents accidental dispatch of available trucks
- Ensures allocation process is completed first

### Consignment Presence Validation
```java
if (consignments.isEmpty()) {
    throw new RuntimeException("No consignments allocated to this truck");
}
```

**Rationale:**
- Empty dispatches have no business purpose
- Prevents orphaned truck status changes
- Ensures cargo is properly documented

---

## Error Handling

### Truck Not Found
```java
throw new RuntimeException("Truck not found");
```
- HTTP 404 in handler
- Invalid truck ID provided

### Invalid Truck Status
```java
throw new RuntimeException("Truck must be in Allocated or Loading status");
```
- HTTP 409 (Conflict) in handler
- Truck not ready for dispatch

### No Consignments
```java
throw new RuntimeException("No consignments allocated to this truck");
```
- HTTP 409 (Conflict) in handler
- Allocation not completed or error in data

### JSON Serialization Errors
- ObjectMapper exceptions propagate
- Wrapped in RuntimeException
- Handled by global exception handler

### Database Errors
- JOOQ exceptions propagate
- Transaction rollback on failure
- Ensures data consistency

---

## Dispatch Status Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                 Dispatch Status Lifecycle                    │
└─────────────────────────────────────────────────────────────┘

    ┌─────────────┐
    │  Dispatched │ ←── DispatchService.create()
    └──────┬──────┘
           │
           │ Truck en route
           │
     ┌─────▼──────┐
     │ InTransit  │ ←── TruckService.updateStatus()
     └─────┬──────┘
           │
           │ Truck arrives at destination
           │ TruckService.updateStatus()
           │
    ┌──────▼───────────┐
    │   Delivered      │ ←── Final state
    └──────────────────┘
```

**Note:** Dispatch status is updated automatically when truck returns:
```java
// In TruckService.updateStatus()
if ("Available".equals(newStatus) && wasInTransit) {
    dsl.update(DISPATCH_DOCUMENTS)
        .set(DISPATCH_DOCUMENTS.DISPATCH_STATUS, "Delivered")
        .set(DISPATCH_DOCUMENTS.ARRIVAL_TIME, OffsetDateTime.now())
        .where(DISPATCH_DOCUMENTS.TRUCK_ID.eq(id))
        .and(DISPATCH_DOCUMENTS.DISPATCH_STATUS.eq("InTransit"))
        .execute();
}
```

---

## Performance Considerations

### Query Optimization
- Index on `dispatch_documents(truck_id)` for truck lookup
- Index on `dispatch_documents(dispatch_status)` for filtering
- Index on `consignments(assigned_truck_id, status)` for consignment fetch

### Transaction Scope
- Dispatch creation involves multiple updates
- All operations in single transaction
- Rollback on any failure ensures consistency

### Manifest Size
- Manifest stored as JSON in database
- Large manifests (100+ consignments) could impact performance
- Consider pagination for very large dispatches

---

## Testing Guidelines

### Create Dispatch
```java
DispatchDocumentsRecord dispatch = dispatchService.create(
    truckId,
    OffsetDateTime.now(),
    user
);

assert dispatch.getDispatchId() != null;
assert dispatch.getDispatchStatus().equals("Dispatched");
assert dispatch.getTotalConsignments() > 0;
assert dispatch.getConsignmentManifest() != null;
```

### Get All Dispatches
```java
var dispatches = dispatchService.getAll("Dispatched", "Mumbai");
assert dispatches.stream()
    .allMatch(d -> d.getDispatchStatus().equals("Dispatched"));
assert dispatches.stream()
    .allMatch(d -> d.getDestination().contains("Mumbai"));
```

### Get Dispatch By ID
```java
DispatchDocumentsRecord dispatch = dispatchService.getById(dispatchId);
assert dispatch != null;
assert dispatch.getDispatchId().equals(dispatchId);
```

### Validate Manifest Content
```java
var manifest = dispatch.getConsignmentManifest();
var manifestData = mapper.readValue(manifest.data(), new TypeReference<List<Map>>() {});
assert !manifestData.isEmpty();
assert manifestData.get(0).containsKey("consignmentNumber");
assert manifestData.get(0).containsKey("volume");
```
