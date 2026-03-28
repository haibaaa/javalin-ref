package com.tccs.handler;

import com.tccs.db.tables.records.PricingRulesRecord;
import com.tccs.dto.ApiResponse;
import com.tccs.service.PricingService;
import io.javalin.http.Context;

import java.util.Map;

public class PricingHandler {
    private final PricingService pricingService;

    public PricingHandler(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    public void getAll(Context ctx) {
        ctx.json(ApiResponse.ok(Map.of("rules", pricingService.getAll())));
    }

    public void create(Context ctx) {
        PricingRulesRecord rule = ctx.bodyAsClass(PricingRulesRecord.class);
        ctx.status(201).json(ApiResponse.ok(pricingService.create(rule)));
    }
}
