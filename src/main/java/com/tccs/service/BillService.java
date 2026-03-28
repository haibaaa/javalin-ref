package com.tccs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tccs.db.tables.records.BillsRecord;
import com.tccs.db.tables.records.PricingRulesRecord;
import org.jooq.DSLContext;
import org.jooq.JSON;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.tccs.db.Tables.*;

public class BillService {
    private final DSLContext dsl;
    private final ObjectMapper mapper;
    private final PricingService pricingService;

    public record BillResult(BillsRecord bill, BigDecimal finalCharge, Map<String, Object> pricingBreakdown) {}

    public BillService(DSLContext dsl, ObjectMapper mapper, PricingService pricingService) {
        this.dsl = dsl;
        this.mapper = mapper;
        this.pricingService = pricingService;
    }

    public BillResult generateBill(String consignmentNumber, BigDecimal volume, String destination, OffsetDateTime registrationDate) throws Exception {
        PricingRulesRecord rule = pricingService.getActiveRule(destination, LocalDate.now());
        if (rule == null) {
            throw new RuntimeException("No active pricing rule found for destination: " + destination);
        }

        BigDecimal baseCharge = volume.multiply(rule.getRatePerCubicMeter()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalCharge = baseCharge.max(rule.getMinimumCharge()).setScale(2, RoundingMode.HALF_UP);
        String appliedRule = (finalCharge.compareTo(rule.getMinimumCharge()) == 0 &&
                             baseCharge.compareTo(rule.getMinimumCharge()) < 0) ? "minimum" : "rate";

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("volume", volume);
        breakdown.put("destination", destination);
        breakdown.put("ratePerCubicMeter", rule.getRatePerCubicMeter());
        breakdown.put("minimumCharge", rule.getMinimumCharge());
        breakdown.put("baseCharge", baseCharge);
        breakdown.put("finalCharge", finalCharge);
        breakdown.put("appliedRule", appliedRule);
        breakdown.put("calculatedAt", OffsetDateTime.now().toString());

        BillsRecord bill = dsl.newRecord(BILLS);
        bill.setBillId(UUID.randomUUID());
        bill.setConsignmentNumber(consignmentNumber);
        bill.setTransportCharges(finalCharge);
        bill.setRegistrationDate(registrationDate != null ? registrationDate : OffsetDateTime.now());
        bill.setPricingBreakdown(JSON.json(mapper.writeValueAsString(breakdown)));
        bill.store();

        // Update consignment charges
        dsl.update(CONSIGNMENTS)
                .set(CONSIGNMENTS.TRANSPORT_CHARGES, finalCharge)
                .where(CONSIGNMENTS.CONSIGNMENT_NUMBER.eq(consignmentNumber))
                .execute();

        return new BillResult(bill, finalCharge, breakdown);
    }
}
