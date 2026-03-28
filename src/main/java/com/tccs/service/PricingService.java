package com.tccs.service;

import com.tccs.db.tables.records.PricingRulesRecord;
import org.jooq.DSLContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.tccs.db.Tables.PRICING_RULES;

public class PricingService {
    private final DSLContext dsl;

    public PricingService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<PricingRulesRecord> getAll() {
        return dsl.selectFrom(PRICING_RULES).fetch();
    }

    public PricingRulesRecord getActiveRule(String destination, LocalDate date) {
        return dsl.selectFrom(PRICING_RULES)
                .where(PRICING_RULES.DESTINATION.eq(destination))
                .and(PRICING_RULES.IS_ACTIVE.isTrue())
                .and(PRICING_RULES.EFFECTIVE_DATE.le(date))
                .and(PRICING_RULES.EXPIRY_DATE.isNull().or(PRICING_RULES.EXPIRY_DATE.ge(date)))
                .fetchOne();
    }

    public PricingRulesRecord create(PricingRulesRecord rule) {
        rule.setId(UUID.randomUUID());
        rule.store();
        return rule;
    }
}
