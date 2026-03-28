# Documentation: DashboardHandler.java

## Executive Summary

`DashboardHandler.java` provides the endpoint for retrieving dashboard statistics.
It aggregates data from multiple sources to display truck status distribution,
consignment status distribution, recent consignments, and pending volume metrics
in a single API call for the application's main dashboard view.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-18**

**Purpose:** Define the handler with required dependencies.

**Technical Breakdown:**
- **Dependencies:**
  - `DSLContext dsl` - Direct database access for statistics
  - `AllocationService allocationService` - Pending volume data
- **Constructor Injection:** Both dependencies provided via constructor

**Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database query execution |
| `allocationService` | AllocationService | Pending volume retrieval |

---

### 2. Get Stats Handler
**Lines 20-40**

**Purpose:** Retrieve comprehensive dashboard statistics.

**Method Signature:**
```java
public void getStats(Context ctx)
```

**Request Format:**
```
GET /api/dashboard/stats
Authorization: Bearer <token>
```

**Logic Flow:**
```
1. Query truck counts grouped by status
2. Query consignment counts grouped by status
3. Fetch 5 most recent consignments
4. Get pending volumes from AllocationService
5. Aggregate all data into single response
```

**Database Queries:**

**Truck Statistics:**
```sql
SELECT status, COUNT(*) 
FROM trucks 
GROUP BY status
```

**Consignment Statistics:**
```sql
SELECT status, COUNT(*) 
FROM consignments 
GROUP BY status
```

**Recent Consignments:**
```sql
SELECT * FROM consignments 
ORDER BY registration_timestamp DESC 
LIMIT 5
```

**Success Response (200):**
```json
{
  "data": {
    "trucks": {
      "Available": 5,
      "Allocated": 2,
      "InTransit": 3,
      "Loading": 1
    },
    "consignments": {
      "Registered": 12,
      "Pending": 8,
      "AllocatedToTruck": 5,
      "InTransit": 10,
      "Delivered": 45
    },
    "recentConsignments": [
      {
        "consignmentNumber": "TCCS-20240101-0042",
        "volume": 150.5,
        "destination": "Mumbai",
        "status": "Registered",
        "registrationTimestamp": "2024-01-01T14:30:00Z"
      }
    ],
    "pendingVolumes": [
      {
        "destination": "Mumbai",
        "pendingVolume": 450.5,
        "consignmentCount": 4,
        "thresholdPercentage": 90.1,
        "threshold": 500.0,
        "nearingThreshold": true
      }
    ]
  }
}
```

**Response Sections:**

| Section | Description |
|---------|-------------|
| `trucks` | Map of status → count for all truck statuses |
| `consignments` | Map of status → count for all consignment statuses |
| `recentConsignments` | Array of 5 most recently created consignments |
| `pendingVolumes` | Array of destinations with pending volume metrics |

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `AllocationService` | Pending volume data | [AllocationService.java](../service/AllocationService.java.md) |
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |
| `Role` | Authorization enum | [Role.java](../security/Role.java.md) |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context
- **org.jooq.DSLContext** - Database query interface
- **org.jooq.impl.DSL** - JOOQ static methods
- **com.tccs.db.Tables** - JOOQ table references (TRUCKS, CONSIGNMENTS)

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("dashboard", () -> {
    get("stats", dashboardHandler::getStats, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/dashboard/stats` | GET | BranchOperator, TransportManager, SystemAdministrator | Get dashboard statistics |

---

## Dashboard Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                  Dashboard Stats Flow                        │
└─────────────────────────────────────────────────────────────┘

                    GET /api/dashboard/stats
                              │
                              ▼
                    DashboardHandler.getStats()
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  Truck Stats    │ │ Consignment     │ │  Recent         │
│  (DB Query)     │ │ Stats (DB Query)│ │  Consignments   │
│                 │ │                 │ │  (DB Query)     │
│  SELECT status, │ │ SELECT status,  │ │ SELECT * FROM   │
│  COUNT(*)       │ │ COUNT(*)        │ │ consignments    │
│  FROM trucks    │ │ FROM consign-   │ │ ORDER BY        │
│  GROUP BY       │ │ ments           │ │ timestamp DESC  │
│  status         │ │ GROUP BY status │ │ LIMIT 5         │
└─────────────────┘ └─────────────────┘ └─────────────────┘
         │                    │                    │
         │                    │                    ▼
         │                    │          ┌─────────────────┐
         │                    │          │ AllocationService│
         │                    │          │ .getPendingVol- │
         │                    │          │ umes()          │
         │                    │          └─────────────────┘
         │                    │                    │
         └────────────────────┴────────────────────┘
                              │
                              ▼
                    Aggregate into response
                              │
                              ▼
                    Return JSON to client
```

---

## Use Cases

### Use Case 1: Operations Dashboard
**Scenario:** Branch Operator logs in and needs an overview of current operations.

**Dashboard Displays:**
- Truck availability (how many trucks available vs in transit)
- Consignment pipeline (how many pending vs delivered)
- Recent activity (last 5 consignments)
- Allocation watchlist (destinations nearing threshold)

### Use Case 2: Capacity Monitoring
**Scenario:** Transport Manager monitors capacity utilization.

**Key Metrics:**
- Truck status distribution identifies bottlenecks
- Pending volumes show allocation pipeline
- Recent consignments show current throughput

---

## Integration Points

### AllocationService
Pending volumes retrieved from [AllocationService](../service/AllocationService.java.md):
```java
"pendingVolumes", allocationService.getPendingVolumes()
```

### Database Tables
Direct queries on:
- `TRUCKS` - Truck status counts
- `CONSIGNMENTS` - Consignment stats and recent records

---

## Performance Considerations

### Query Optimization
- All queries use indexed columns (status, registration_timestamp)
- GROUP BY queries are efficient on properly indexed tables
- LIMIT 5 on recent consignments prevents large result sets

### Caching Opportunities
For high-traffic dashboards, consider:
- Response caching (5-10 second TTL)
- Materialized views for status counts
- WebSocket for real-time updates

---

## Testing Guidelines

### Get Dashboard Stats
```bash
curl -X GET http://localhost:8080/api/dashboard/stats \
  -H "Authorization: Bearer $TOKEN"
```

### Expected Response Structure
```json
{
  "data": {
    "trucks": { "...": "..." },
    "consignments": { "...": "..." },
    "recentConsignments": [ "...", "...", "...", "...", "..." ],
    "pendingVolumes": [ "...", "..." ]
  }
}
```
