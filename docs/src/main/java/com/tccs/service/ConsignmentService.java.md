# Documentation: ConsignmentService.java

## Executive Summary

`ConsignmentService.java` manages the complete lifecycle of consignments from
registration through delivery. It handles consignment creation with automatic
number generation, billing integration, allocation triggering, status updates
with audit logging, and provides flexible querying capabilities with filtering
and pagination support.

---

## Logical Block Analysis

### 1. Class Structure & Dependencies
**Lines 8-24**

**Purpose:** Define the service with required dependencies.

**Technical Breakdown:**
- **Dependencies:**
  - `DSLContext dsl` - Database operations via JOOQ
  - `ObjectMapper mapper` - JSON serialization for audit logs
  - `BillService billService` - Bill generation
  - `AllocationService allocationService` - Auto-allocation

**Record Definition:**
```java
public record ConsignmentCreateResult(
    ConsignmentsRecord consignment,     // Created consignment
    BillService.BillResult bill,        // Generated bill
    AllocationService.AllocationResult allocation  // Allocation result
) {}
```

**Key Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database operations |
| `mapper` | ObjectMapper | JSON log serialization |
| `billService` | BillService | Billing operations |
| `allocationService` | AllocationService | Allocation logic |

---

### 2. Get All Consignments Method
**Lines 26-47**

**Purpose:** Retrieve consignments with flexible filtering and pagination.

**Method Signature:**
```java
public List<ConsignmentsRecord> getAll(String status, String destination, 
    String search, String startDate, String endDate, int limit, int offset)
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `status` | String | Filter by status (exact match) |
| `destination` | String | Filter by destination (partial match) |
| `search` | String | Search in consignment number, addresses |
| `startDate` | String | Filter by registration date (ISO format) |
| `endDate` | String | Filter by registration date (ISO format) |
| `limit` | int | Maximum results (default: 50) |
| `offset` | int | Pagination offset (default: 0) |

**Condition Building Logic:**
```java
Condition cond = DSL.noCondition();

if (status != null) 
    cond = cond.and(CONSIGNMENTS.STATUS.eq(status));

if (destination != null) 
    cond = cond.and(CONSIGNMENTS.DESTINATION.containsIgnoreCase(destination));

if (search != null) 
    cond = cond.and(CONSIGNMENTS.CONSIGNMENT_NUMBER.containsIgnoreCase(search)
        .or(CONSIGNMENTS.SENDER_ADDRESS.containsIgnoreCase(search))
        .or(CONSIGNMENTS.RECEIVER_ADDRESS.containsIgnoreCase(search)));

if (startDate != null) 
    cond = cond.and(CONSIGNMENTS.REGISTRATION_TIMESTAMP.ge(
        OffsetDateTime.parse(startDate + "T00:00:00Z")));

if (endDate != null) 
    cond = cond.and(CONSIGNMENTS.REGISTRATION_TIMESTAMP.le(
        OffsetDateTime.parse(endDate + "T23:59:59Z")));
```

**Database Query:**
```sql
SELECT * FROM consignments
WHERE [dynamic conditions]
ORDER BY registration_timestamp DESC
LIMIT ? OFFSET ?
```

**Returns:** List of `ConsignmentsRecord` matching criteria

---

### 3. Count All Consignments Method
**Lines 49-66**

**Purpose:** Count consignments matching filter criteria (for pagination).

**Method Signature:**
```java
public int countAll(String status, String destination, 
    String search, String startDate, String endDate)
```

**Logic:**
- Builds same conditions as `getAll()`
- Executes `COUNT(*)` query instead of SELECT
- Returns total count for pagination metadata

**Usage:**
```java
int total = consignmentService.countAll(status, destination, search, startDate, endDate);
int totalPages = (total + limit - 1) / limit;
```

---

### 4. Create Consignment Method
**Lines 68-99**

**Purpose:** Create a new consignment with automatic billing and allocation.

**Method Signature:**
```java
public ConsignmentCreateResult create(BigDecimal volume, String destination, 
    String senderAddress, String receiverAddress, UsersRecord user) throws Exception
```

**Logic Flow:**

**Step 1: Generate Consignment Number (Lines 70-75)**
```java
OffsetDateTime now = OffsetDateTime.now();
OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);

long todayCount = dsl.fetchCount(CONSIGNMENTS, 
    CONSIGNMENTS.REGISTRATION_TIMESTAMP.ge(startOfDay));

String consignmentNumber = String.format("TCCS-%s-%04d",
    now.format(DateTimeFormatter.ofPattern("yyyyMMdd")), todayCount + 1);
```

**Format:** `TCCS-YYYYMMDD-NNNN`
- Daily counter resets at midnight UTC
- Zero-padded to 4 digits

**Step 2: Initialize Status Log (Lines 77-81)**
```java
List<Map<String, Object>> log = List.of(Map.of(
    "oldStatus", "",
    "newStatus", "Registered",
    "timestamp", now.toString(),
    "note", "Consignment registered"
));
```

**Step 3: Create Consignment Record (Lines 83-91)**
```java
ConsignmentsRecord c = dsl.newRecord(CONSIGNMENTS);
c.setConsignmentNumber(consignmentNumber);
c.setVolume(volume);
c.setDestination(destination);
c.setSenderAddress(senderAddress);
c.setReceiverAddress(receiverAddress);
c.setRegistrationTimestamp(now);
c.setStatus("Registered");
c.setStatusChangeLog(JSON.json(mapper.writeValueAsString(log)));
c.setCreatedBy(user != null ? user.getUserId() : null);
c.store();
```

**Step 4: Generate Bill (Line 93)**
```java
var billResult = billService.generateBill(consignmentNumber, volume, destination, now);
```

**Step 5: Trigger Allocation (Line 94)**
```java
var allocationResult = allocationService.checkAndTriggerAllocation(destination);
```

**Step 6: Refresh and Return (Lines 96-98)**
```java
c.refresh();  // Get updated transport_charges from DB
return new ConsignmentCreateResult(c, billResult, allocationResult);
```

---

### 5. Get By ID Method
**Lines 101-104**

**Purpose:** Retrieve a single consignment by its consignment number.

**Method Signature:**
```java
public ConsignmentsRecord getById(String id)
```

**Query:**
```sql
SELECT * FROM consignments
WHERE consignment_number = ?
```

**Returns:** `ConsignmentsRecord` or null if not found

---

### 6. Update Status Method
**Lines 106-124**

**Purpose:** Update consignment status with audit logging.

**Method Signature:**
```java
public void updateStatus(String id, String newStatus, String note, UsersRecord user) 
    throws Exception
```

**Logic Flow:**

**Step 1: Retrieve Consignment (Line 108)**
```java
ConsignmentsRecord c = getById(id);
if (c == null) return;  // Silent fail - could throw exception instead
```

**Step 2: Parse Existing Log (Lines 110-111)**
```java
List<Map<String, Object>> log = mapper.readValue(
    c.getStatusChangeLog().data(), 
    new TypeReference<>() {}
);
```

**Step 3: Create Log Entry (Lines 112-117)**
```java
Map<String, Object> entry = new LinkedHashMap<>();
entry.put("oldStatus", c.getStatus());
entry.put("newStatus", newStatus);
entry.put("timestamp", OffsetDateTime.now().toString());
entry.put("note", note);
if (user != null) entry.put("updatedBy", user.getName());
log.add(entry);
```

**Step 4: Update Record (Lines 119-122)**
```java
c.setStatus(newStatus);
c.setStatusChangeLog(JSON.json(mapper.writeValueAsString(log)));
c.store();
```

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `BillService` | Bill generation | [BillService.java](BillService.java.md) |
| `AllocationService` | Allocation logic | [AllocationService.java](AllocationService.java.md) |
| `ConsignmentsRecord` | JOOQ record | Database schema |
| `UsersRecord` | User record | Database schema |
| `ConsignmentHandler` | HTTP endpoint | [ConsignmentHandler.java](../handler/ConsignmentHandler.java.md) |
| `DispatchService` | Dispatch creation | [DispatchService.java](DispatchService.java.md) |

### External Dependencies

- **org.jooq.DSLContext** - Database query interface
- **org.jooq.Condition** - Dynamic query building
- **org.jooq.JSON** - JSON type handling
- **org.jooq.impl.DSL** - JOOQ static methods
- **com.fasterxml.jackson.databind.ObjectMapper** - JSON serialization
- **com.fasterxml.jackson.core.type.TypeReference** - Type-safe JSON parsing
- **java.time.OffsetDateTime, ZoneOffset, DateTimeFormatter** - Date/time handling

---

## Consignment Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│               Consignment Status Lifecycle                   │
└─────────────────────────────────────────────────────────────┘

    ┌─────────────┐
    │  Registered │ ←── ConsignmentService.create()
    └──────┬──────┘
           │
           │ Automatic allocation check
           │ If volume < threshold
           │
     ┌─────▼──────┐
     │  Pending   │ ←── Awaiting sufficient volume
     └─────┬──────┘
           │
           │ Volume >= threshold
           │ OR manual allocation
           │
    ┌──────▼───────────┐
    │ AllocatedToTruck │ ←── Assigned to specific truck
    └──────┬───────────┘
           │
           │ Dispatch created
           │ ConsignmentService.updateStatus()
           │
    ┌──────▼───────────┐
    │   InTransit      │ ←── Truck en route
    └──────┬───────────┘
           │
           │ Truck arrives at destination
           │ TruckService.updateStatus()
           │
    ┌──────▼───────────┐
    │   Delivered      │ ←── Final state
    └──────────────────┘

    Alternative paths:
    ──────────────────
    Registered → Cancelled (customer request)
    Pending → Cancelled (operational issue)
    AllocatedToTruck → Cancelled (rare, requires admin)
```

---

## Consignment Number Format

### Structure
```
TCCS-YYYYMMDD-NNNN
 │    │       │
 │    │       └─ Sequential number (0001-9999)
 │    └───────── Date (YYYYMMDD)
 └────────────── Company prefix
```

### Examples
- `TCCS-20240101-0001` - First consignment on Jan 1, 2024
- `TCCS-20240101-0042` - 42nd consignment on Jan 1, 2024
- `TCCS-20240102-0001` - First consignment on Jan 2, 2024

### Generation Logic
```java
// Count consignments created today
long todayCount = dsl.fetchCount(CONSIGNMENTS, 
    CONSIGNMENTS.REGISTRATION_TIMESTAMP.ge(startOfDay));

// Next number is count + 1
String consignmentNumber = String.format("TCCS-%s-%04d",
    now.format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
    todayCount + 1);
```

---

## Status Change Log Structure

### JSON Format
```json
[
  {
    "oldStatus": "",
    "newStatus": "Registered",
    "timestamp": "2024-01-01T10:00:00Z",
    "note": "Consignment registered"
  },
  {
    "oldStatus": "Registered",
    "newStatus": "Pending",
    "timestamp": "2024-01-01T10:05:00Z",
    "note": "Awaiting volume threshold (450.50m³ / 500m³)"
  },
  {
    "oldStatus": "Pending",
    "newStatus": "AllocatedToTruck",
    "timestamp": "2024-01-01T14:30:00Z",
    "note": "Allocated to truck MH-12-AB-1234",
    "updatedBy": "Transport Manager"
  }
]
```

### Log Entry Fields

| Field | Type | Description |
|-------|------|-------------|
| `oldStatus` | String | Previous status |
| `newStatus` | String | New status |
| `timestamp` | String | ISO 8601 timestamp |
| `note` | String | Reason for change |
| `updatedBy` | String | User name (optional) |

---

## Integration Points

### BillService
[BillService](BillService.java.md) - Automatic billing on creation:
```java
var billResult = billService.generateBill(consignmentNumber, volume, destination, now);
```

### AllocationService
[AllocationService](AllocationService.java.md) - Automatic allocation check:
```java
var allocationResult = allocationService.checkAndTriggerAllocation(destination);
```

### ConsignmentHandler
[ConsignmentHandler](../handler/ConsignmentHandler.java.md) - HTTP endpoints:
```java
var result = consignmentService.create(volume, destination, senderAddress, receiverAddress, user);
```

### DispatchService
[DispatchService](DispatchService.java.md) - Status updates during dispatch:
```java
consignmentService.updateStatus(consignmentNumber, "InTransit", note, user);
```

### TruckService
[TruckService](TruckService.java.md) - Status updates on truck return:
```java
// TruckService updates consignments to "Delivered"
dsl.update(CONSIGNMENTS)
   .set(CONSIGNMENTS.STATUS, "Delivered")
   .where(CONSIGNMENTS.ASSIGNED_TRUCK_ID.eq(id))
   .execute();
```

---

## Query Examples

### Find All Registered Consignments
```java
var registered = consignmentService.getAll("Registered", null, null, null, null, 50, 0);
```

### Search by Consignment Number
```java
var result = consignmentService.getAll(null, null, "TCCS-20240101", null, null, 10, 0);
```

### Filter by Destination and Date Range
```java
var mumbaiConsignments = consignmentService.getAll(
    null, "Mumbai", null, "2024-01-01", "2024-01-31", 100, 0
);
```

### Paginated Results
```java
int limit = 20;
int page = 3;
int offset = (page - 1) * limit;

var page3 = consignmentService.getAll(null, null, null, null, null, limit, offset);
```

---

## Business Rules

### Consignment Creation
- Consignment number must be unique
- Daily counter resets at midnight UTC
- Volume must be positive
- Destination must have active pricing rule
- Sender and receiver addresses are required

### Status Transitions
- Registered → Pending (automatic, below threshold)
- Registered/Pending → AllocatedToTruck (allocation)
- AllocatedToTruck → InTransit (dispatch)
- InTransit → Delivered (truck return)
- Any status → Cancelled (with restrictions)

### Audit Requirements
- All status changes must be logged
- Log includes timestamp, old/new status, note
- User attribution for manual changes
- Logs stored as JSON in database

---

## Error Handling

### Missing Pricing Rule
```java
// From BillService
throw new RuntimeException("No active pricing rule found for destination: " + destination);
```

### Status Update on Non-existent Consignment
```java
ConsignmentsRecord c = getById(id);
if (c == null) return;  // Silent fail
```

### JSON Serialization Errors
- Caught and wrapped in RuntimeException
- Propagates to global exception handler

### Database Errors
- JOOQ exceptions propagate to caller
- Transaction rollback on failure

---

## Performance Considerations

### Query Optimization
- Index on `consignments(status)` for filtering
- Index on `consignments(destination)` for filtering
- Index on `consignments(registration_timestamp)` for sorting
- Composite index on `(status, destination, registration_timestamp)` ideal

### Pagination
- Always use limit/offset for large datasets
- Count query for total pages
- Consider keyset pagination for deep paging

### N+1 Prevention
- `getAll()` returns full records
- Related data (truck, dispatches) loaded separately
- Consider JOIN fetch for related data

---

## Testing Guidelines

### Create Consignment
```java
ConsignmentCreateResult result = consignmentService.create(
    new BigDecimal("150.5"),
    "Mumbai",
    "123 Sender St, Delhi",
    "456 Receiver Ave, Mumbai",
    user
);

assert result.consignment().getConsignmentNumber().startsWith("TCCS-");
assert result.consignment().getStatus().equals("Registered");
assert result.bill() != null;
```

### Update Status
```java
consignmentService.updateStatus(
    "TCCS-20240101-0001",
    "InTransit",
    "Dispatched via truck MH-12-AB-1234",
    user
);

ConsignmentsRecord c = consignmentService.getById("TCCS-20240101-0001");
assert c.getStatus().equals("InTransit");
```

### Query with Filters
```java
var list = consignmentService.getAll("Registered", "Mumbai", null, null, null, 50, 0);
assert list.stream().allMatch(c -> c.getStatus().equals("Registered"));
assert list.stream().allMatch(c -> c.getDestination().contains("Mumbai"));
```
