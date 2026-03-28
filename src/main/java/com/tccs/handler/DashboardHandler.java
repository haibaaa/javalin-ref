package com.tccs.handler;

import com.tccs.dto.ApiResponse;
import com.tccs.service.AllocationService;
import com.tccs.service.TruckService;
import io.javalin.http.Context;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.Map;

import static com.tccs.db.Tables.*;

public class DashboardHandler {
    private final DSLContext dsl;
    private final AllocationService allocationService;

    public DashboardHandler(DSLContext dsl, AllocationService allocationService) {
        this.dsl = dsl;
        this.allocationService = allocationService;
    }

    public void getStats(Context ctx) {
        var truckStats = dsl.select(TRUCKS.STATUS, DSL.count())
                .from(TRUCKS)
                .groupBy(TRUCKS.STATUS)
                .fetchMap(TRUCKS.STATUS, DSL.count());

        var consignmentStats = dsl.select(CONSIGNMENTS.STATUS, DSL.count())
                .from(CONSIGNMENTS)
                .groupBy(CONSIGNMENTS.STATUS)
                .fetchMap(CONSIGNMENTS.STATUS, DSL.count());

        var recentConsignments = dsl.selectFrom(CONSIGNMENTS)
                .orderBy(CONSIGNMENTS.REGISTRATION_TIMESTAMP.desc())
                .limit(5)
                .fetch();

        ctx.json(ApiResponse.ok(Map.of(
                "trucks", truckStats,
                "consignments", consignmentStats,
                "recentConsignments", recentConsignments,
                "pendingVolumes", allocationService.getPendingVolumes()
        )));
    }
}
