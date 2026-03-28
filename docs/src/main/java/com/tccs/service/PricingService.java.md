# Documentation: PricingService.java

## Executive Summary

`PricingService.java` manages pricing rules for transport charge calculation.
It provides methods to retrieve all pricing rules, find active rules for a
specific destination and date, and create new pricing rules. The service
ensures that the correct pricing is applied based on destination and temporal
validity.

---

## Logical Block Analysis

### 1. Class Structure & Dependencies
**Lines 8-15**

**Purpose:** Define the service with database dependency.

**Technical Breakdown:**
- **Dependency:** `DSLContext dsl` - Database operations via JOOQ
- **Table Reference:** `PRICING_RULES` - JOOQ-generated table reference

**Key Field:**
| Field | Type | Purpose |
|-------|------|---------|
| `dsl` | DSLContext | Database query execution |

---

### 2. Get All Pricing Rules Method
**Lines 17-20**

**Purpose:** Retrieve all pricing rules from the database.

**Method Signature:**
```java
public List<PricingRulesRecord> getAll()
```

**Database Query:**
```sql
SELECT * FROM pricing_rules
ORDER BY destination, effective_date
```

**Returns:** List of all `PricingRulesRecord` objects

**Usage:**
- Admin UI for pricing rule management
- Audit and review of pricing configuration
- Bulk export of pricing data

---

### 3. Get Active Rule Method
**Lines 22-30**

**Purpose:** Find the active pricing rule for a destination on a specific date.

**Method Signature:**
```java
public PricingRulesRecord getActiveRule(String destination, LocalDate date)
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `destination` | String | Target destination |
| `date` | LocalDate | Date for which to find active rule |

**Database Query:**
```sql
SELECT * FROM pricing_rules
WHERE destination = ?
  AND is_active = TRUE
  AND effective_date <= ?
  AND (expiry_date IS NULL OR expiry_date >= ?)
ORDER BY effective_date DESC
LIMIT 1
```

**Query Logic:**

| Condition | Purpose |
|-----------|---------|
| `destination = ?` | Match target destination |
| `is_active = TRUE` | Only active rules |
| `effective_date <= ?` | Rule must be in effect |
| `expiry_date IS NULL OR expiry_date >= ?` | Rule must not be expired |

**Returns:** `PricingRulesRecord` or `null` if no active rule found

**Usage:**
```java
// In BillService.generateBill()
PricingRulesRecord rule = pricingService.getActiveRule(destination, LocalDate.now());
if (rule == null) {
    throw new RuntimeException("No active pricing rule found for destination: " + destination);
}
```

---

### 4. Create Pricing Rule Method
**Lines 32-37**

**Purpose:** Create and persist a new pricing rule.

**Method Signature:**
```java
public PricingRulesRecord create(PricingRulesRecord rule)
```

**Logic Flow:**
```java
rule.setId(UUID.randomUUID());  // Generate new UUID
rule.store();                   // INSERT into database
return rule;                    // Return persisted record
```

**Required Fields in Rule:**

| Field | Type | Description |
|-------|------|-------------|
| `destination` | String | Target destination |
| `ratePerCubicMeter` | BigDecimal | Rate per m³ |
| `minimumCharge` | BigDecimal | Minimum charge |
| `isActive` | Boolean | Active status |
| `effectiveDate` | LocalDate | When rule becomes effective |

**Optional Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `expiryDate` | LocalDate | When rule expires (null = no expiry) |

**Returns:** The created `PricingRulesRecord` with generated ID

---

## Dependency & Cross-Reference

### Internal Dependencies

| Class | Purpose | Documentation Link |
|-------|---------|-------------------|
| `PricingRulesRecord` | JOOQ pricing rule record | Database schema |
| `BillService` | Uses rules for billing | [BillService.java](BillService.java.md) |
| `PricingHandler` | HTTP endpoint | [PricingHandler.java](../handler/PricingHandler.java.md) |

### External Dependencies

- **org.jooq.DSLContext** - Database query interface
- **com.tccs.db.Tables.PRICING_RULES** - JOOQ table reference
- **java.time.LocalDate** - Date handling

---

## Pricing Rule Structure

### PRICING_RULES Table

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY | Unique rule identifier |
| `destination` | VARCHAR | NOT NULL | Target destination |
| `rate_per_cubic_meter` | DECIMAL | NOT NULL | Rate per m³ |
| `minimum_charge` | DECIMAL | NOT NULL | Minimum charge |
| `is_active` | BOOLEAN | DEFAULT TRUE | Active status |
| `effective_date` | DATE | NOT NULL | When rule becomes effective |
| `expiry_date` | DATE | NULL | When rule expires |
| `created_at` | TIMESTAMP | DEFAULT NOW() | Creation time |

### Rule Validity Period

```
                    ┌─────────────────────────┐
                    │     Rule Validity       │
                    │                         │
effective_date ────►│─────────────────────────├──► expiry_date (or ∞)
                    │                         │
                    │   Rule is ACTIVE        │
                    └─────────────────────────┘
                         │           │
                         │           │
                    date >=     date <=
                    effective   expiry (or null)
```

**Active Rule Conditions:**
1. `is_active = TRUE`
2. `effective_date ≤ current_date`
3. `expiry_date IS NULL OR expiry_date ≥ current_date`

---

## Pricing Rule Examples

### Example 1: Standard Route (No Expiry)

```java
PricingRulesRecord mumbaiRule = dsl.newRecord(PRICING_RULES);
mumbaiRule.setDestination("Mumbai");
mumbaiRule.setRatePerCubicMeter(new BigDecimal("5.00"));
mumbaiRule.setMinimumCharge(new BigDecimal("100.00"));
mumbaiRule.setIsActive(true);
mumbaiRule.setEffectiveDate(LocalDate.of(2024, 1, 1));
mumbaiRule.setExpiryDate(null);  // No expiry

pricingService.create(mumbaiRule);
```

**Effect:**
- Applies to all consignments to Mumbai from Jan 1, 2024 onwards
- No expiration date (indefinite)
- Can be superseded by newer rule with later effective_date

### Example 2: Seasonal Pricing (With Expiry)

```java
PricingRulesRecord festiveRule = dsl.newRecord(PRICING_RULES);
festiveRule.setDestination("Delhi");
festiveRule.setRatePerCubicMeter(new BigDecimal("6.00"));
festiveRule.setMinimumCharge(new BigDecimal("120.00"));
festiveRule.setIsActive(true);
festiveRule.setEffectiveDate(LocalDate.of(2024, 10, 1));
festiveRule.setExpiryDate(LocalDate.of(2024, 11, 30));

pricingService.create(festiveRule);
```

**Effect:**
- Applies only during festive season (Oct-Nov 2024)
- Automatically expires after Nov 30, 2024
- Previous rule becomes active again after expiry

### Example 3: Rate Change (Rule Versioning)

```java
// Old rule (until Dec 31, 2023)
PricingRulesRecord oldRule = dsl.newRecord(PRICING_RULES);
oldRule.setDestination("Bangalore");
oldRule.setRatePerCubicMeter(new BigDecimal("4.50"));
oldRule.setMinimumCharge(new BigDecimal("90.00"));
oldRule.setEffectiveDate(LocalDate.of(2023, 1, 1));
oldRule.setExpiryDate(LocalDate.of(2023, 12, 31));

// New rule (from Jan 1, 2024)
PricingRulesRecord newRule = dsl.newRecord(PRICING_RULES);
newRule.setDestination("Bangalore");
newRule.setRatePerCubicMeter(new BigDecimal("5.00"));
newRule.setMinimumCharge(new BigDecimal("100.00"));
newRule.setEffectiveDate(LocalDate.of(2024, 1, 1));
newRule.setExpiryDate(null);

pricingService.create(oldRule);
pricingService.create(newRule);
```

**Effect:**
- Seamless rate transition on Jan 1, 2024
- Historical consignments retain original pricing
- No data migration required

---

## Integration Points

### BillService
[BillService](BillService.java.md) retrieves active rule for billing:
```java
PricingRulesRecord rule = pricingService.getActiveRule(destination, LocalDate.now());
```

### PricingHandler
[PricingHandler](../handler/PricingHandler.java.md) exposes HTTP endpoints:
```java
public void getAll(Context ctx) {
    ctx.json(ApiResponse.ok(Map.of("rules", pricingService.getAll())));
}

public void create(Context ctx) {
    PricingRulesRecord rule = ctx.bodyAsClass(PricingRulesRecord.class);
    ctx.status(201).json(ApiResponse.ok(pricingService.create(rule)));
}
```

---

## Business Rules

### Rule Uniqueness
- Multiple rules can exist for same destination
- Only one rule should be active at any given time
- Overlapping date ranges should be avoided

### Rule Precedence
When multiple rules match:
1. Filter by `is_active = TRUE`
2. Filter by date range validity
3. Order by `effective_date DESC`
4. Take first result (most recent effective date)

### Rate Changes
- To change rates, create new rule with new `effective_date`
- Set `expiry_date` on old rule (optional, for clarity)
- Historical data unaffected (uses rule valid at time of consignment)

### Minimum Charge
- Ensures profitability for small shipments
- Applied when `volume × rate < minimum_charge`
- Covers fixed operational costs

---

## Query Optimization

### Recommended Indexes

```sql
-- For getActiveRule query
CREATE INDEX idx_pricing_rules_destination_active 
ON pricing_rules(destination, is_active, effective_date DESC);

-- For date range filtering
CREATE INDEX idx_pricing_rules_dates 
ON pricing_rules(effective_date, expiry_date);
```

### Query Performance
- `getActiveRule` uses indexed columns
- Returns single record (LIMIT 1)
- Efficient for high-frequency billing operations

---

## Error Handling

### No Active Rule Found
```java
PricingRulesRecord rule = pricingService.getActiveRule(destination, date);
if (rule == null) {
    throw new RuntimeException("No active pricing rule found for destination: " + destination);
}
```

**Causes:**
- No rule configured for destination
- Rule exists but not yet effective
- Rule has expired
- Rule is marked inactive

**Resolution:**
- Create pricing rule for destination
- Ensure effective_date is in the past
- Remove or extend expiry_date
- Set is_active = TRUE

### Database Errors
- JOOQ exceptions propagate to caller
- Transaction rollback on failure
- Handled by global exception handler in Main.java

---

## Administrative Operations

### View All Rules
```bash
curl -X GET http://localhost:8080/api/pricing \
  -H "Authorization: Bearer $TOKEN"
```

### Create New Rule
```bash
curl -X POST http://localhost:8080/api/pricing \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "destination": "Chennai",
    "ratePerCubicMeter": 5.50,
    "minimumCharge": 110.00,
    "isActive": true,
    "effectiveDate": "2024-01-01"
  }'
```

### Deactivate Rule (Manual)
```sql
UPDATE pricing_rules
SET is_active = FALSE, expiry_date = CURRENT_DATE
WHERE id = 'rule-uuid';
```

---

## Testing Guidelines

### Get Active Rule
```java
PricingRulesRecord rule = pricingService.getActiveRule("Mumbai", LocalDate.now());

assert rule != null;
assert rule.getDestination().equals("Mumbai");
assert rule.getIsActive();
assert rule.getEffectiveDate().isBefore(LocalDate.now());
```

### Get All Rules
```java
List<PricingRulesRecord> rules = pricingService.getAll();

assert !rules.isEmpty();
assert rules.stream()
    .allMatch(r -> r.getDestination() != null);
```

### Create Rule
```java
PricingRulesRecord newRule = dsl.newRecord(PRICING_RULES);
newRule.setDestination("Test City");
newRule.setRatePerCubicMeter(new BigDecimal("10.00"));
newRule.setMinimumCharge(new BigDecimal("50.00"));
newRule.setIsActive(true);
newRule.setEffectiveDate(LocalDate.now());

PricingRulesRecord created = pricingService.create(newRule);

assert created.getId() != null;
assert created.getDestination().equals("Test City");
```

### Test Historical Rule
```java
LocalDate pastDate = LocalDate.of(2023, 1, 1);
PricingRulesRecord historicalRule = pricingService.getActiveRule("Mumbai", pastDate);

// Should return rule that was active on that date
assert historicalRule != null;
assert historicalRule.getEffectiveDate().isBefore(pastDate);
```
