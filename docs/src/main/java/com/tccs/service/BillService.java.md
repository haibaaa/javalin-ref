# Documentation: BillService.java

## Executive Summary

`BillService.java` handles automatic bill generation and transport charge
calculation for consignments. It retrieves active pricing rules, calculates
charges based on volume and destination, applies minimum charge rules, and
maintains detailed pricing breakdowns for transparency and auditing purposes.

---

## Logical Block Analysis

### 1. Class Structure & Dependencies
**Lines 8-22**

**Purpose:** Define the service with required dependencies.

**Technical Breakdown:**
- **Dependencies:**
  - `DSLContext dsl` - Database operations via JOOQ
  - `ObjectMapper mapper` - JSON serialization for pricing breakdown
  - `PricingService pricingService` - Pricing rule retrieval

**Record Definition:**
```java
public record BillResult(
    BillsRecord bill,              // Generated bill record
    BigDecimal finalCharge,        // Final charge amount
    Map<String, Object> pricingBreakdown  // Detailed calculation
) {}
```

**Key Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database operations |
| `mapper` | ObjectMapper | JSON serialization |
| `pricingService` | PricingService | Pricing rule access |

---

### 2. Generate Bill Method
**Lines 24-62**

**Purpose:** Generate a bill for a consignment with detailed pricing calculation.

**Method Signature:**
```java
public BillResult generateBill(String consignmentNumber, BigDecimal volume, 
                               String destination, OffsetDateTime registrationDate) 
    throws Exception
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `consignmentNumber` | String | Consignment identifier |
| `volume` | BigDecimal | Cargo volume in m³ |
| `destination` | String | Destination city |
| `registrationDate` | OffsetDateTime | When consignment was registered |

**Logic Flow:**

**Step 1: Retrieve Pricing Rule (Lines 26-29)**
```java
PricingRulesRecord rule = pricingService.getActiveRule(destination, LocalDate.now());
if (rule == null) {
    throw new RuntimeException("No active pricing rule found for destination: " + destination);
}
```
- Fetches active pricing rule for destination and current date
- Throws exception if no rule found (must be handled by caller)

**Step 2: Calculate Charges (Lines 31-35)**
```java
BigDecimal baseCharge = volume.multiply(rule.getRatePerCubicMeter())
    .setScale(2, RoundingMode.HALF_UP);

BigDecimal finalCharge = baseCharge.max(rule.getMinimumCharge())
    .setScale(2, RoundingMode.HALF_UP);
```

**Calculation Logic:**
```
baseCharge = volume × ratePerCubicMeter

finalCharge = MAX(baseCharge, minimumCharge)
```

**Applied Rule Determination (Lines 36-38)**
```java
String appliedRule = (finalCharge.compareTo(rule.getMinimumCharge()) == 0 &&
                     baseCharge.compareTo(rule.getMinimumCharge()) < 0) 
                     ? "minimum" : "rate";
```

**Step 3: Build Pricing Breakdown (Lines 40-48)**
```java
Map<String, Object> breakdown = new HashMap<>();
breakdown.put("volume", volume);
breakdown.put("destination", destination);
breakdown.put("ratePerCubicMeter", rule.getRatePerCubicMeter());
breakdown.put("minimumCharge", rule.getMinimumCharge());
breakdown.put("baseCharge", baseCharge);
breakdown.put("finalCharge", finalCharge);
breakdown.put("appliedRule", appliedRule);
breakdown.put("calculatedAt", OffsetDateTime.now().toString());
```

**Step 4: Create Bill Record (Lines 50-56)**
```java
BillsRecord bill = dsl.newRecord(BILLS);
bill.setBillId(UUID.randomUUID());
bill.setConsignmentNumber(consignmentNumber);
bill.setTransportCharges(finalCharge);
bill.setRegistrationDate(registrationDate != null ? registrationDate : OffsetDateTime.now());
bill.setPricingBreakdown(JSON.json(mapper.writeValueAsString(breakdown)));
bill.store();
```

**Step 5: Update Consignment Charges (Lines 58-61)**
```java
dsl.update(CONSIGNMENTS)
    .set(CONSIGNMENTS.TRANSPORT_CHARGES, finalCharge)
    .where(CONSIGNMENTS.CONSIGNMENT_NUMBER.eq(consignmentNumber))
    .execute();
```

**Returns:** `BillResult` containing bill record, final charge, and pricing breakdown

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `PricingService` | Pricing rule retrieval | [PricingService.java](PricingService.java.md) |
| `PricingRulesRecord` | JOOQ pricing rule record | Database schema |
| `BillsRecord` | JOOQ bill record | Database schema |
| `ConsignmentService` | Consignment operations | [ConsignmentService.java](ConsignmentService.java.md) |

### External Dependencies

- **org.jooq.DSLContext** - Database query interface
- **org.jooq.JSON** - JSON type handling
- **com.fasterxml.jackson.databind.ObjectMapper** - JSON serialization
- **java.math.BigDecimal** - Precise decimal calculations
- **java.math.RoundingMode** - Rounding strategy
- **java.time.LocalDate, OffsetDateTime** - Date/time handling

---

## Pricing Calculation Algorithm

```
┌─────────────────────────────────────────────────────────────┐
│                 Transport Charge Calculation                 │
└─────────────────────────────────────────────────────────────┘

Input:
  - volume: 150.5 m³
  - destination: "Mumbai"
  - date: 2024-01-01

1. Retrieve Active Pricing Rule
   ──────────────────────────────
   SELECT * FROM pricing_rules
   WHERE destination = "Mumbai"
     AND is_active = TRUE
     AND effective_date <= CURRENT_DATE
     AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
   
   Result:
   {
     "ratePerCubicMeter": 5.00,
     "minimumCharge": 100.00
   }

2. Calculate Base Charge
   ──────────────────────────────
   baseCharge = volume × ratePerCubicMeter
   baseCharge = 150.5 × 5.00 = 752.50

3. Apply Minimum Charge Rule
   ──────────────────────────────
   finalCharge = MAX(baseCharge, minimumCharge)
   finalCharge = MAX(752.50, 100.00) = 752.50
   
   appliedRule = "rate" (since baseCharge > minimumCharge)

4. Build Pricing Breakdown
   ──────────────────────────────
   {
     "volume": 150.5,
     "destination": "Mumbai",
     "ratePerCubicMeter": 5.00,
     "minimumCharge": 100.00,
     "baseCharge": 752.50,
     "finalCharge": 752.50,
     "appliedRule": "rate",
     "calculatedAt": "2024-01-01T10:00:00Z"
   }

5. Persist Bill
   ──────────────────────────────
   INSERT INTO bills (bill_id, consignment_number, 
                      transport_charges, pricing_breakdown)
   VALUES (uuid, "TCCS-20240101-0001", 752.50, breakdown_json)
```

---

## Pricing Examples

### Example 1: Large Shipment (Rate Applied)

| Parameter | Value |
|-----------|-------|
| Volume | 150.5 m³ |
| Destination | Mumbai |
| Rate | ₹5.00/m³ |
| Minimum | ₹100.00 |

**Calculation:**
```
baseCharge = 150.5 × 5.00 = ₹752.50
finalCharge = MAX(752.50, 100.00) = ₹752.50
appliedRule = "rate"
```

### Example 2: Small Shipment (Minimum Applied)

| Parameter | Value |
|-----------|-------|
| Volume | 10.0 m³ |
| Destination | Delhi |
| Rate | ₹4.50/m³ |
| Minimum | ₹80.00 |

**Calculation:**
```
baseCharge = 10.0 × 4.50 = ₹45.00
finalCharge = MAX(45.00, 80.00) = ₹80.00
appliedRule = "minimum"
```

### Example 3: Exactly at Minimum

| Parameter | Value |
|-----------|-------|
| Volume | 20.0 m³ |
| Destination | Bangalore |
| Rate | ₹4.00/m³ |
| Minimum | ₹80.00 |

**Calculation:**
```
baseCharge = 20.0 × 4.00 = ₹80.00
finalCharge = MAX(80.00, 80.00) = ₹80.00
appliedRule = "rate" (equal, so rate is preferred)
```

---

## Database Schema Reference

### BILLS Table

| Column | Type | Description |
|--------|------|-------------|
| `bill_id` | UUID | Primary key |
| `consignment_number` | VARCHAR | Foreign key to consignments |
| `transport_charges` | DECIMAL | Final charge amount |
| `registration_date` | TIMESTAMP | When consignment was registered |
| `pricing_breakdown` | JSON | Detailed calculation details |
| `created_at` | TIMESTAMP | Bill creation time |

### Pricing Breakdown JSON Structure

```json
{
  "volume": 150.5,
  "destination": "Mumbai",
  "ratePerCubicMeter": 5.00,
  "minimumCharge": 100.00,
  "baseCharge": 752.50,
  "finalCharge": 752.50,
  "appliedRule": "rate",
  "calculatedAt": "2024-01-01T10:00:00Z"
}
```

---

## Integration Points

### ConsignmentService
[ConsignmentService](ConsignmentService.java.md) calls bill generation during consignment creation:
```java
var billResult = billService.generateBill(consignmentNumber, volume, destination, now);
```

### PricingService
[PricingService](PricingService.java.md) provides active pricing rules:
```java
PricingRulesRecord rule = pricingService.getActiveRule(destination, LocalDate.now());
```

### ConsignmentHandler
[ConsignmentHandler](../handler/ConsignmentHandler.java.md) returns pricing breakdown in response:
```java
"pricingBreakdown", result.bill().pricingBreakdown()
```

---

## Business Rules

### Pricing Rule Application
- Only active rules are considered
- Rule must be effective on the consignment date
- Expired rules are not used
- First matching rule is applied

### Minimum Charge Purpose
- Ensures profitability for small shipments
- Covers fixed operational costs (fuel, driver, paperwork)
- Prevents loss-leading on very small consignments

### Charge Updates
- Consignment's `transport_charges` field is updated immediately
- Bill record is created as audit trail
- Pricing breakdown is preserved for transparency

### Rounding Strategy
- All amounts rounded to 2 decimal places
- Uses `RoundingMode.HALF_UP` (standard commercial rounding)
- Ensures consistent calculations

---

## Error Handling

### No Pricing Rule Found
```java
throw new RuntimeException("No active pricing rule found for destination: " + destination);
```
- Caller must handle this exception
- Typically results in 400 or 500 HTTP response
- Indicates missing configuration

### JSON Serialization Errors
- Propagate as `JsonProcessingException`
- Wrapped in RuntimeException by Jackson
- Should be rare with valid data

### Database Errors
- JOOQ exceptions propagate to caller
- Transaction rollback on failure
- Handled by global exception handler

---

## Audit & Transparency

### Pricing Breakdown Benefits
- **Customer Service:** Explain charges to customers
- **Dispute Resolution:** Prove calculation accuracy
- **Auditing:** Verify pricing rule application
- **Analytics:** Analyze revenue by rate vs minimum

### Bill Record Purpose
- Permanent record of each transaction
- Links consignment to charges
- Enables revenue reporting
- Supports financial auditing

---

## Performance Considerations

### Query Optimization
- Pricing rule lookup should use index on `(destination, is_active, effective_date)`
- Single query per bill generation
- No N+1 query issues

### Caching Opportunities
- Pricing rules change infrequently
- Consider caching active rules per destination
- Cache invalidation on rule changes

### Transaction Scope
- Bill creation and consignment update in same transaction
- Ensures data consistency
- Rollback on any failure

---

## Testing Guidelines

### Verify Bill Generation
```java
BillResult result = billService.generateBill(
    "TCCS-20240101-0001",
    new BigDecimal("150.5"),
    "Mumbai",
    OffsetDateTime.now()
);

assert result.finalCharge().compareTo(new BigDecimal("752.50")) == 0;
assert result.pricingBreakdown().get("appliedRule").equals("rate");
```

### Test Minimum Charge Application
```java
BillResult result = billService.generateBill(
    "TCCS-20240101-0002",
    new BigDecimal("10.0"),
    "Delhi",
    OffsetDateTime.now()
);

assert result.pricingBreakdown().get("appliedRule").equals("minimum");
```

### Test Missing Rule
```java
assertThrows(RuntimeException.class, () -> {
    billService.generateBill(
        "TCCS-20240101-0003",
        new BigDecimal("100.0"),
        "Unknown City",
        OffsetDateTime.now()
    );
});
```
