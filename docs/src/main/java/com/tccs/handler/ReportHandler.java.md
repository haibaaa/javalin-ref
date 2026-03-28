# Documentation: ReportHandler.java

## Executive Summary

`ReportHandler.java` implements HTTP request handlers for revenue reporting
and data export. It provides endpoints for generating revenue analytics (by
destination and daily) and exporting report data to CSV format. The handler
performs direct database queries using JOOQ to aggregate consignment data
for business intelligence purposes.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-18**

**Purpose:** Define the handler with database dependency.

**Technical Breakdown:**
- **Dependency:** `DSLContext dsl` - Direct database access for reporting
- **Constructor Injection:** Dependency provided via constructor

**Field:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database query execution |

---

### 2. Revenue Report Handler
**Lines 20-62**

**Purpose:** Generate comprehensive revenue analytics with multiple views.

**Method Signature:**
```java
public void revenue(Context ctx)
```

**Request Format:**
```
GET /api/reports/revenue?[filters]
Authorization: Bearer <token>
```

**Query Parameters:**

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `startDate` | Date | Report start date (ISO format) | 2 years ago |
| `endDate` | Date | Report end date (ISO format) | Today |

**Example Request:**
```
GET /api/reports/revenue?startDate=2024-01-01&endDate=2024-03-31
```

**Logic Flow:**
```
1. Parse date parameters (use defaults if not provided)
2. Query revenue grouped by destination
3. Query daily revenue trend
4. Query overall summary statistics
5. Aggregate all data into single response
```

**Database Queries:**

**Query 1: Revenue by Destination**
```sql
SELECT 
    destination,
    COUNT(*) AS total_consignments,
    SUM(transport_charges) AS total_revenue,
    AVG(transport_charges) AS avg_revenue,
    SUM(volume) AS total_volume,
    COUNT(*) FILTER (WHERE status = 'Delivered') AS delivered_count,
    COUNT(*) FILTER (WHERE status = 'Cancelled') AS cancelled_count
FROM consignments
WHERE DATE(registration_timestamp) BETWEEN start AND end
  AND status != 'Cancelled'
GROUP BY destination
ORDER BY total_revenue DESC
```

**Query 2: Daily Revenue**
```sql
SELECT 
    DATE(registration_timestamp) AS date,
    SUM(transport_charges) AS revenue,
    COUNT(*) AS consignments
FROM consignments
WHERE DATE(registration_timestamp) BETWEEN start AND end
  AND status != 'Cancelled'
GROUP BY DATE(registration_timestamp)
ORDER BY date ASC
```

**Query 3: Summary**
```sql
SELECT 
    SUM(transport_charges) AS total_revenue,
    COUNT(*) AS total_consignments,
    AVG(transport_charges) AS avg_charge,
    SUM(volume) AS total_volume
FROM consignments
WHERE DATE(registration_timestamp) BETWEEN start AND end
  AND status != 'Cancelled'
```

**Success Response (200):**
```json
{
  "data": {
    "revenueByDestination": [
      {
        "destination": "Mumbai",
        "total_consignments": 50,
        "total_revenue": 25000.00,
        "avg_revenue": 500.00,
        "total_volume": 5000.0,
        "delivered_count": 45,
        "cancelled_count": 0
      }
    ],
    "dailyRevenue": [
      {
        "date": "2024-01-01",
        "revenue": 1500.00,
        "consignments": 3
      }
    ],
    "summary": {
      "total_revenue": 25000.00,
      "total_consignments": 50,
      "avg_charge": 500.00,
      "total_volume": 5000.0
    },
    "dateRange": {
      "start": "2024-01-01",
      "end": "2024-03-31"
    }
  }
}
```

**Response Sections:**

| Section | Description |
|---------|-------------|
| `revenueByDestination` | Revenue breakdown by destination, sorted by revenue |
| `dailyRevenue` | Day-by-day revenue trend |
| `summary` | Overall statistics for the period |
| `dateRange` | Applied date range for the report |

---

### 3. Export CSV Handler
**Lines 64-87**

**Purpose:** Export revenue data as CSV file for external analysis.

**Method Signature:**
```java
public void exportCsv(Context ctx)
```

**Request Format:**
```
GET /api/reports/export/csv?[filters]
Authorization: Bearer <token>
```

**Query Parameters:**

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `startDate` | Date | Export start date | 2 years ago |
| `endDate` | Date | Export end date | Today |

**Logic Flow:**
```
1. Parse date parameters (use defaults if not provided)
2. Query revenue grouped by destination
3. Build CSV content in memory
4. Set response headers for file download
5. Return CSV content
```

**CSV Format:**
```csv
Destination,Consignments,Revenue,Volume
Mumbai,50,25000.00,5000.0
Delhi,30,15000.00,3000.0
Bangalore,20,12000.00,2000.0
```

**Response Headers:**
```
Content-Type: text/csv
Content-Disposition: attachment; filename=report.csv
```

**Response Body:**
Raw CSV content as plain text

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |
| `Role` | Authorization enum | [Role.java](../security/Role.java.md) |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context
- **org.jooq.DSLContext** - Database query interface
- **org.jooq.impl.DSL** - JOOQ static methods
- **com.tccs.db.Tables** - JOOQ table references (CONSIGNMENTS)
- **java.time.LocalDate** - Date handling

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("reports", () -> {
    get("revenue", reportHandler::revenue, Role.TransportManager, Role.SystemAdministrator);
    get("export/csv", reportHandler::exportCsv, Role.TransportManager, Role.SystemAdministrator);
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/reports/revenue` | GET | TransportManager, SystemAdministrator | Revenue analytics |
| `/api/reports/export/csv` | GET | TransportManager, SystemAdministrator | CSV export |

**Security Note:** Reports contain sensitive business data; restricted to
management roles only.

---

## Report Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Revenue Report Flow                       │
└─────────────────────────────────────────────────────────────┘

                GET /api/reports/revenue
                          │
                          ▼
                  ReportHandler.revenue()
                          │
         ┌────────────────┼────────────────┐
         │                │                │
         ▼                ▼                ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────┐
│ Revenue by      │ │ Daily       │ │ Summary     │
│ Destination     │ │ Revenue     │ │ Statistics  │
│                 │ │             │ │             │
│ GROUP BY        │ │ GROUP BY    │ │ Aggregates  │
│ destination     │ │ date        │ │ only        │
│ ORDER BY        │ │ ORDER BY    │ │             │
│ revenue DESC    │ │ date ASC    │ │             │
└─────────────────┘ └─────────────┘ └─────────────┘
         │                │                │
         └────────────────┴────────────────┘
                          │
                          ▼
                  Aggregate response
                          │
                          ▼
                  Return JSON
```

---

## Use Cases

### Use Case 1: Monthly Revenue Review
**Scenario:** Transport Manager prepares monthly revenue report.

**Steps:**
1. GET `/api/reports/revenue?startDate=2024-01-01&endDate=2024-01-31`
2. Review revenue by destination
3. Identify top-performing routes
4. Analyze daily trends for anomalies

### Use Case 2: Quarterly Business Analysis
**Scenario:** System Administrator exports data for quarterly review.

**Steps:**
1. GET `/api/reports/export/csv?startDate=2024-01-01&endDate=2024-03-31`
2. Import CSV into spreadsheet software
3. Create charts and pivot tables
4. Present to stakeholders

### Use Case 3: Route Performance Analysis
**Scenario:** Identify underperforming destinations.

**Analysis:**
- Compare revenue per destination
- Review average charges
- Check consignment volumes
- Assess delivery success rates

---

## Key Metrics Explained

### Revenue Metrics

| Metric | Description | Business Insight |
|--------|-------------|------------------|
| `total_revenue` | Sum of all transport charges | Overall business performance |
| `avg_revenue` | Average charge per consignment | Pricing effectiveness |
| `total_volume` | Sum of all cargo volumes | Capacity utilization |

### Volume Metrics

| Metric | Description | Business Insight |
|--------|-------------|------------------|
| `total_consignments` | Count of consignments | Business volume |
| `delivered_count` | Successfully delivered | Operational efficiency |
| `cancelled_count` | Cancelled consignments | Customer satisfaction issue |

---

## Integration Points

### Database Tables
Direct queries on `CONSIGNMENTS` table:
- `destination` - Grouping dimension
- `transport_charges` - Revenue metric
- `volume` - Cargo metric
- `status` - Filtering and counting
- `registration_timestamp` - Date filtering

---

## Performance Considerations

### Query Optimization
- Queries use `registration_timestamp` index for date filtering
- `destination` should be indexed for GROUP BY performance
- Consider materialized views for frequently accessed reports

### Large Date Ranges
- Default limit of 2 years prevents excessive data
- Daily revenue can produce many rows for long periods
- Consider pagination for very large datasets

### Memory Usage
- CSV export builds entire file in memory
- For large exports, consider streaming response
- Current implementation suitable for typical use cases

---

## Testing Guidelines

### Get Revenue Report
```bash
curl -X GET "http://localhost:8080/api/reports/revenue?startDate=2024-01-01&endDate=2024-03-31" \
  -H "Authorization: Bearer $MANAGER_TOKEN"
```

### Export CSV
```bash
curl -X GET "http://localhost:8080/api/reports/export/csv?startDate=2024-01-01&endDate=2024-03-31" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -o report.csv
```

### View CSV Content
```bash
cat report.csv
```

---

## CSV Export Format

### File Structure
```
Destination,Consignments,Revenue,Volume
Mumbai,50,25000.00,5000.0
Delhi,30,15000.00,3000.0
```

### Column Definitions

| Column | Type | Description |
|--------|------|-------------|
| `Destination` | String | Destination name |
| `Consignments` | Integer | Number of consignments |
| `Revenue` | Decimal | Total revenue (₹) |
| `Volume` | Decimal | Total volume (m³) |

### Import Tips
- Open in Excel/Google Sheets for analysis
- Use as data source for BI tools
- Suitable for pivot table creation
