# Documentation: PricingHandler.java

## Executive Summary

`PricingHandler.java` implements HTTP request handlers for pricing rule
management. It provides endpoints for retrieving all pricing rules and creating
new pricing rules. Pricing rules determine how transport charges are calculated
based on destination, volume, and rate per cubic meter.

---

## Logical Block Analysis

### 1. Class Structure & Constructor
**Lines 8-14**

**Purpose:** Define the handler with its service dependency.

**Technical Breakdown:**
- **Dependency:** `PricingService pricingService` - Pricing rule operations
- **Constructor Injection:** Dependency provided via constructor

**Field:**
| Field | Type | Purpose |
|-------|------|---------|
| `pricingService` | PricingService | Pricing rule CRUD operations |

---

### 2. Get All Pricing Rules Handler
**Lines 16-20**

**Purpose:** Retrieve all pricing rules from the system.

**Method Signature:**
```java
public void getAll(Context ctx)
```

**Request Format:**
```
GET /api/pricing
Authorization: Bearer <token>
```

**Logic Flow:**
```
1. Call pricingService.getAll()
2. Wrap result in Map with key "rules"
3. Return as API response
```

**Success Response (200):**
```json
{
  "data": {
    "rules": [
      {
        "id": "rule-uuid",
        "destination": "Mumbai",
        "ratePerCubicMeter": 5.00,
        "minimumCharge": 100.00,
        "isActive": true,
        "effectiveDate": "2024-01-01",
        "expiryDate": null
      },
      {
        "id": "rule-uuid",
        "destination": "Delhi",
        "ratePerCubicMeter": 4.50,
        "minimumCharge": 80.00,
        "isActive": true,
        "effectiveDate": "2024-01-01",
        "expiryDate": "2024-12-31"
      }
    ]
  }
}
```

**Pricing Rule Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique rule identifier |
| `destination` | String | Target destination |
| `ratePerCubicMeter` | BigDecimal | Rate per m³ |
| `minimumCharge` | BigDecimal | Minimum charge regardless of volume |
| `isActive` | Boolean | Whether rule is active |
| `effectiveDate` | LocalDate | When rule becomes effective |
| `expiryDate` | LocalDate | When rule expires (null = no expiry) |

---

### 3. Create Pricing Rule Handler
**Lines 22-26**

**Purpose:** Create a new pricing rule.

**Method Signature:**
```java
public void create(Context ctx)
```

**Request Format:**
```json
POST /api/pricing
Content-Type: application/json
Authorization: Bearer <token>

{
  "destination": "Bangalore",
  "ratePerCubicMeter": 6.00,
  "minimumCharge": 120.00,
  "isActive": true,
  "effectiveDate": "2024-01-01",
  "expiryDate": null
}
```

**Required Fields:**
- `destination` - Target destination name
- `ratePerCubicMeter` - Rate per cubic meter
- `minimumCharge` - Minimum charge amount
- `isActive` - Active status

**Optional Fields:**
- `effectiveDate` - Effective date (defaults based on DB schema)
- `expiryDate` - Expiry date (null = no expiry)

**Logic Flow:**
```
1. Parse request body as PricingRulesRecord
2. Call pricingService.create() which:
   a. Generates new UUID for rule
   b. Saves rule to database
3. Return created rule
```

**Success Response (201):**
```json
{
  "data": {
    "id": "new-rule-uuid",
    "destination": "Bangalore",
    "ratePerCubicMeter": 6.00,
    "minimumCharge": 120.00,
    "isActive": true,
    "effectiveDate": "2024-01-01",
    "expiryDate": null
  }
}
```

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `PricingService` | Pricing business logic | [PricingService.java](../service/PricingService.java.md) |
| `PricingRulesRecord` | JOOQ record type | Database schema |
| `ApiResponse` | Response wrapper | [ApiResponse.java](../dto/ApiResponse.java.md) |

### External Dependencies

- **io.javalin.http.Context** - HTTP request/response context

---

## Route Configuration

Defined in [Main.java](../Main.java.md):

```java
path("pricing", () -> {
    get(pricingHandler::getAll, Role.BranchOperator, Role.TransportManager, Role.SystemAdministrator);
    post(pricingHandler::create, Role.SystemAdministrator);
});
```

### Authorization Matrix

| Endpoint | Method | Required Role | Description |
|----------|--------|---------------|-------------|
| `/api/pricing` | GET | BranchOperator, TransportManager, SystemAdministrator | View pricing rules |
| `/api/pricing` | POST | SystemAdministrator only | Create pricing rule |

**Security Note:** Only SystemAdministrators can create pricing rules, as pricing
directly impacts revenue and billing.

---

## Pricing Calculation

### How Charges Are Calculated

When a consignment is created, the `BillService` uses pricing rules:

```
baseCharge = volume × ratePerCubicMeter
finalCharge = max(baseCharge, minimumCharge)
```

**Example Calculation:**

| Scenario | Volume | Rate | Minimum | Base Charge | Final Charge |
|----------|--------|------|---------|-------------|--------------|
| Small shipment | 10 m³ | ₹5/m³ | ₹100 | ₹50 | ₹100 (minimum applied) |
| Large shipment | 100 m³ | ₹5/m³ | ₹100 | ₹500 | ₹500 (rate applied) |

### Rule Selection

For a given destination and date:
1. Find rules where `destination` matches
2. Filter by `isActive = true`
3. Filter by `effectiveDate ≤ consignment date`
4. Filter by `expiryDate IS NULL OR expiryDate ≥ consignment date`
5. Use the first matching rule

---

## Integration Points

### BillService
[BillService](../service/BillService.java.md) retrieves active rule for billing:
```java
PricingRulesRecord rule = pricingService.getActiveRule(destination, LocalDate.now());
```

### Consignment Creation
[ConsignmentHandler](ConsignmentHandler.java.md) triggers billing which uses pricing:
```java
var billResult = billService.generateBill(consignmentNumber, volume, destination, now);
```

---

## Business Rules

### Destination Uniqueness
- Multiple rules can exist for the same destination
- Only one rule should be active at any given time
- Overlapping date ranges should be avoided

### Rate Changes
- To change rates, create a new rule with new effective date
- Old rules should have expiry date set
- Historical consignments retain original pricing

### Minimum Charge Purpose
- Ensures profitability for small shipments
- Covers fixed costs regardless of volume
- Typically set based on operational cost analysis

---

## Testing Guidelines

### Get All Pricing Rules
```bash
curl -X GET http://localhost:8080/api/pricing \
  -H "Authorization: Bearer $TOKEN"
```

### Create Pricing Rule
```bash
curl -X POST http://localhost:8080/api/pricing \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "Bangalore",
    "ratePerCubicMeter": 6.00,
    "minimumCharge": 120.00,
    "isActive": true,
    "effectiveDate": "2024-01-01"
  }'
```

---

## Administrative Notes

### Pricing Strategy
- Rates should reflect distance, road conditions, and demand
- Minimum charges should cover fixed operational costs
- Regular review and adjustment recommended

### Audit Trail
- Pricing changes affect revenue
- Only SystemAdministrators should have create permissions
- Consider adding update/delete endpoints with audit logging
