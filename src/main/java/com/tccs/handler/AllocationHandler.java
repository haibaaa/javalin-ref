package com.tccs.handler;

import com.tccs.dto.ApiResponse;
import com.tccs.service.AllocationService;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AllocationHandler {
    private final AllocationService allocationService;

    public AllocationHandler(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    public void trigger(Context ctx) throws Exception {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String destination = body.get("destination");
        if (destination == null) {
            ctx.status(400).json(ApiResponse.error("Destination is required"));
            return;
        }
        var result = allocationService.checkAndTriggerAllocation(destination);
        ctx.json(ApiResponse.ok(result));
    }

    public void getPendingVolumes(Context ctx) {
        ctx.json(ApiResponse.ok(Map.of("pendingVolumes", allocationService.getPendingVolumes())));
    }
}
