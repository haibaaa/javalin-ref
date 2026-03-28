# Documentation: AllocationService.java

## Executive Summary

`AllocationService.java` implements the core consignment-to-truck allocation
algorithm. It automatically groups consignments by destination and assigns them
to suitable trucks when the total volume reaches a configured threshold. The
service manages the allocation lifecycle, updates both consignment and truck
records, and maintains detailed audit logs of all allocation activities.

---

## Logical Block Analysis

### 1. Class Structure & Dependencies
**Lines 8-24**

**Purpose:** Define the service with required dependencies and configuration.

**Technical Breakdown:**
- **Dependencies:**
  - `DSLContext dsl` - Database operations via JOOQ
  - `ObjectMapper mapper` - JSON serialization for audit logs
  - `AppConfig config` - Configuration for allocation threshold
- **Configuration:**
  - `allocationThreshold` - Volume threshold (m³) to trigger allocation (default: 500.0)

**Record Definition:**
```java
public record AllocationResult(
    boolean triggered,      // Was allocation executed?
    String reason,          // Human-readable explanation
    BigDecimal totalVolume, // Total volume considered
    Map<String, Object> truckInfo,  // Assigned truck details
    String destination,     // Target destination
    int consignmentCount,   // Number of consignments allocated
    List<String> consignments,  // Consignment numbers allocated
    boolean noTrucks        // True if no trucks available
) {}
```

**Key Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database query execution |
| `mapper` | ObjectMapper | JSON log serialization |
| `allocationThreshold` | double | Volume threshold for auto-allocation |

---

### 2. Check and Trigger Allocation Method
**Lines 26-94**

**Purpose:** Execute the allocation algorithm for a specific destination.

**Method Signature:**
```java
public AllocationResult checkAndTriggerAllocation(String destination) throws Exception
```

**Logic Flow:**

**Phase 1: Volume Check (Lines 28-43)**
```
1. Query total pending volume for destination:
   SELECT SUM(volume) FROM consignments
   WHERE destination = ? AND status IN ('Registered', 'Pending')

2. If volume < threshold:
   - Mark 'Registered' consignments as 'Pending'
   - Return result with triggered=false
   - Reason: "Volume X m³ < Y m³ threshold"
```

**Phase 2: Truck Search (Lines 45-60)**
```
3. Find suitable trucks:
   SELECT * FROM trucks
   WHERE status = 'Available' AND capacity >= totalVolume
   ORDER BY capacity ASC

4. If no suitable trucks found:
   - Search for any available truck (regardless of capacity)

5. If still no trucks:
   - Return result with triggered=false, noTrucks=true
```

**Phase 3: Allocation Execution (Lines 62-94)**
```
6. Select first available truck

7. Find all pending consignments:
   SELECT * FROM consignments
   WHERE destination = ? AND status IN ('Registered', 'Pending')

8. For each consignment:
   - Update status to 'AllocatedToTruck'
   - Set assignedTruckId
   - Append status change log

9. Update truck:
   - Set status to 'Allocated'
   - Set destination
   - Set cargoVolume = totalVolume
   - Append status history log

10. Return AllocationResult with triggered=true
```

**Database Queries:**

**Volume Check:**
```sql
SELECT SUM(volume)
FROM consignments
WHERE destination = ?
  AND status IN ('Registered', 'Pending')
```

**Truck Search:**
```sql
SELECT * FROM trucks
WHERE status = 'Available'
  AND capacity >= ?
```

**Consignment Update:**
```sql
UPDATE consignments
SET status = 'AllocatedToTruck',
    assigned_truck_id = ?
WHERE destination = ?
  AND status IN ('Registered', 'Pending')
```

**Truck Update:**
```sql
UPDATE trucks
SET status = 'Allocated',
    destination = ?,
    cargo_volume = ?
WHERE truck_id = ?
```

**Return Values:**

| Scenario | triggered | reason | noTrucks |
|----------|-----------|--------|----------|
| Volume below threshold | false | "Volume X m³ < Y m³ threshold" | false |
| No trucks available | false | "No available trucks" | true |
| Allocation successful | true | "Allocation successful" | false |

---

### 3. Get Pending Volumes Method
**Lines 96-117**

**Purpose:** Retrieve pending volume statistics for all destinations.

**Method Signature:**
```java
public List<Map<String, Object>> getPendingVolumes()
```

**Database Query:**
```sql
SELECT 
    destination,
    SUM(volume) AS pending_volume,
    COUNT(*) AS consignment_count
FROM consignments
WHERE status IN ('Registered', 'Pending')
GROUP BY destination
```

**Logic Flow:**
```
1. Query pending volumes grouped by destination
2. For each destination:
   - Calculate threshold percentage
   - Determine if nearing threshold (≥80%)
   - Build result map
3. Return sorted list
```

**Response Structure:**
```java
[
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
```

**Calculated Fields:**
- `thresholdPercentage` = (pendingVolume / threshold) × 100
- `nearingThreshold` = thresholdPercentage ≥ 80

---

### 4. Status Log Append Methods (Private)
**Lines 119-142**

**Purpose:** Maintain audit trail for status changes.

#### appendStatusLog() - Consignments
```java
private void appendStatusLog(ConsignmentsRecord c, String oldStatus, 
                             String newStatus, String note)
```

**Operation:**
```
1. Parse existing status_change_log JSON
2. Create new log entry:
   {
     "oldStatus": "Registered",
     "newStatus": "Pending",
     "timestamp": "2024-01-01T10:00:00Z",
     "note": "Awaiting volume threshold (450.50m³ / 500m³)"
   }
3. Append to log array
4. Serialize back to JSON
5. Update record
```

#### appendTruckStatusLog() - Trucks
```java
private void appendTruckStatusLog(TrucksRecord truck, String newStatus, String note)
```

**Operation:**
```
1. Parse existing status_history JSON
2. Create new log entry:
   {
     "status": "Allocated",
     "timestamp": "2024-01-01T10:00:00Z",
     "note": "Allocated for Mumbai with 5 consignments (520.50m³)"
   }
3. Append to log array
4. Serialize back to JSON
5. Update record
```

**Error Handling:**
- Exceptions during JSON parsing/serialization are caught and ignored
- Prevents allocation failures due to logging issues

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `AppConfig` | Configuration access | [AppConfig.java](../config/AppConfig.java.md) |
| `ConsignmentsRecord` | JOOQ consignment record | Database schema |
| `TrucksRecord` | JOOQ truck record | Database schema |
| `AllocationHandler` | HTTP endpoint | [AllocationHandler.java](../handler/AllocationHandler.java.md) |
| `ConsignmentService` | Consignment operations | [ConsignmentService.java](ConsignmentService.java.md) |
| `DashboardHandler` | Dashboard stats | [DashboardHandler.java](../handler/DashboardHandler.java.md) |

### External Dependencies

- **org.jooq.DSLContext** - Database query interface
- **org.jooq.JSON** - JSON type handling
- **org.jooq.impl.DSL** - JOOQ static methods
- **com.fasterxml.jackson.databind.ObjectMapper** - JSON serialization
- **com.fasterxml.jackson.core.type.TypeReference** - Type-safe JSON parsing

---

## Allocation Algorithm

```
┌─────────────────────────────────────────────────────────────┐
│              Consignment Allocation Algorithm                │
└─────────────────────────────────────────────────────────────┘

Input: destination

1. Calculate Total Pending Volume
   ──────────────────────────────
   SELECT SUM(volume) FROM consignments
   WHERE destination = input
     AND status IN ('Registered', 'Pending')
   
   totalVolume = result OR 0

2. Threshold Check
   ──────────────────────────────
   IF totalVolume < allocationThreshold:
     → Mark as 'Pending'
     → Return (triggered=false, reason="Below threshold")
   
   IF totalVolume >= allocationThreshold:
     → Proceed to truck search

3. Find Suitable Truck
   ──────────────────────────────
   PRIMARY SEARCH:
   SELECT * FROM trucks
   WHERE status = 'Available'
     AND capacity >= totalVolume
   
   IF no results:
     FALLBACK SEARCH:
     SELECT * FROM trucks
     WHERE status = 'Available'
   
   IF still no results:
     → Return (triggered=false, noTrucks=true)

4. Allocate Consignments
   ──────────────────────────────
   SELECT * FROM consignments
   WHERE destination = input
     AND status IN ('Registered', 'Pending')
   
   FOR EACH consignment:
     - status = 'AllocatedToTruck'
     - assigned_truck_id = selected_truck.truck_id
     - Append status log

5. Update Truck
   ──────────────────────────────
   truck.status = 'Allocated'
   truck.destination = input
   truck.cargo_volume = totalVolume
   Append status history

6. Return Success
   ──────────────────────────────
   AllocationResult(
     triggered=true,
     reason="Allocation successful",
     totalVolume=totalVolume,
     truckInfo={id, registrationNumber, driverName},
     destination=input,
     consignmentCount=count,
     consignments=[numbers],
     noTrucks=false
   )
```

---

## Configuration

### Allocation Threshold

**Property:** `tccs.allocation.threshold`

**Default:** 500.0 m³

**Purpose:** Determines when to automatically allocate consignments to trucks.

**Trade-offs:**

| Threshold | Pros | Cons |
|-----------|------|-----|
| Low (e.g., 200 m³) | Faster delivery, responsive | Higher cost per m³, inefficient truck usage |
| High (e.g., 800 m³) | Better truck utilization, lower cost | Delayed delivery, customer dissatisfaction |

**Recommended Setting:**
- Set based on average truck capacity
- Consider customer delivery expectations
- Adjust based on route demand patterns

**Configuration:**
```properties
# config.properties
tccs.allocation.threshold=500.0
```

Or via environment variable:
```bash
export TCCS_ALLOCATION_THRESHOLD=500.0
```

---

## Integration Points

### ConsignmentService
[ConsignmentService](ConsignmentService.java.md) triggers allocation on consignment creation:
```java
var allocationResult = allocationService.checkAndTriggerAllocation(destination);
```

### AllocationHandler
[AllocationHandler](../handler/AllocationHandler.java.md) exposes allocation endpoints:
```java
public void trigger(Context ctx) {
    var result = allocationService.checkAndTriggerAllocation(destination);
}
```

### DashboardHandler
[DashboardHandler](../handler/DashboardHandler.java.md) displays pending volumes:
```java
"pendingVolumes", allocationService.getPendingVolumes()
```

---

## Business Rules

### Allocation Triggers
- **Automatic:** When consignment is created
- **Manual:** Via API endpoint `/api/allocation/trigger`

### Status Transitions

**Consignments:**
```
Registered → Pending → AllocatedToTruck → InTransit → Delivered
```

**Trucks:**
```
Available → Allocated → Loading → InTransit → Available
```

### Truck Selection Priority
1. Prefer trucks with capacity ≥ total volume
2. Fall back to any available truck
3. First matching truck is selected (no optimization)

### Audit Requirements
- All status changes are logged
- Logs include timestamp, old/new status, and reason
- Logs stored as JSON in database

---

## Error Handling

### Volume Calculation Errors
- If SUM returns null, treated as 0
- No exception thrown

### Truck Not Found
- Returns `AllocationResult` with `noTrucks=true`
- Allows caller to handle gracefully

### JSON Serialization Errors
- Caught and ignored in log append methods
- Prevents allocation failure due to logging issues

### Database Errors
- Propagate to caller (ConsignmentService or AllocationHandler)
- Handled by global exception handler in Main.java

---

## Performance Considerations

### Query Optimization
- Index on `consignments(destination, status)` recommended
- Index on `trucks(status, capacity)` recommended
- SUM aggregation is efficient on indexed columns

### Concurrency
- Uses database transactions for consistency
- Multiple allocations for same destination could conflict
- Consider row-level locking for high-concurrency scenarios

### Threshold Tuning
- Monitor `nearingThreshold` metric
- Adjust threshold based on operational data
- Consider dynamic threshold based on truck availability

---

## Testing Guidelines

### Manual Allocation Trigger
```bash
curl -X POST http://localhost:8080/api/allocation/trigger \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"destination":"Mumbai"}'
```

### Get Pending Volumes
```bash
curl -X GET http://localhost:8080/api/allocation/pending-volumes \
  -H "Authorization: Bearer $MANAGER_TOKEN"
```

### Expected Allocation Result
```json
{
  "data": {
    "triggered": true,
    "reason": "Allocation successful",
    "totalVolume": 520.5,
    "truckInfo": {
      "id": "truck-uuid",
      "registrationNumber": "MH-12-AB-1234",
      "driverName": "John Driver"
    },
    "destination": "Mumbai",
    "consignmentCount": 5,
    "consignments": ["TCCS-20240101-0001", "..."],
    "noTrucks": false
  }
}
```
