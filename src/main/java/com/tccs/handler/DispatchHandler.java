package com.tccs.handler;

import com.tccs.db.tables.records.UsersRecord;
import com.tccs.dto.ApiResponse;
import com.tccs.service.DispatchService;
import com.tccs.service.TruckService;
import io.javalin.http.Context;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public class DispatchHandler {
    private final DispatchService dispatchService;
    private final TruckService truckService;

    public DispatchHandler(DispatchService dispatchService, TruckService truckService) {
        this.dispatchService = dispatchService;
        this.truckService = truckService;
    }

    public void getAll(Context ctx) {
        String status = ctx.queryParam("status");
        String destination = ctx.queryParam("destination");
        var list = dispatchService.getAll(status, destination);
        ctx.json(ApiResponse.ok(Map.of("dispatches", list)));
    }

    public void create(Context ctx) throws Exception {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        UUID truckId = UUID.fromString((String) body.get("truckId"));
        OffsetDateTime departureTime = body.get("departureTime") != null ? OffsetDateTime.parse((String) body.get("departureTime")) : null;
        UsersRecord user = ctx.attribute("currentUser");

        var dispatch = dispatchService.create(truckId, departureTime, user);
        ctx.status(201).json(ApiResponse.ok(Map.of("dispatch", dispatch)));
    }

    public void getById(Context ctx) {
        UUID id = UUID.fromString(ctx.pathParam("id"));
        var d = dispatchService.getById(id);
        if (d != null) {
            var truck = truckService.getById(d.getTruckId());
            ctx.json(ApiResponse.ok(Map.of("dispatch", d, "truck", truck)));
        } else {
            ctx.status(404).json(ApiResponse.error("Dispatch not found"));
        }
    }
}
