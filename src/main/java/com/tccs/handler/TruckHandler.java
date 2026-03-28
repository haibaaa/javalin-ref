package com.tccs.handler;

import com.tccs.db.tables.records.UsersRecord;
import com.tccs.dto.ApiResponse;
import com.tccs.service.TruckService;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class TruckHandler {
    private final TruckService truckService;

    public TruckHandler(TruckService truckService) {
        this.truckService = truckService;
    }

    public void getAll(Context ctx) {
        String status = ctx.queryParam("status");
        String destination = ctx.queryParam("destination");
        ctx.json(ApiResponse.ok(Map.of("trucks", truckService.getAll(status, destination))));
    }

    public void getAvailable(Context ctx) {
        ctx.json(ApiResponse.ok(Map.of("trucks", truckService.getAvailable())));
    }

    public void getById(Context ctx) {
        UUID id = UUID.fromString(ctx.pathParam("id"));
        var truck = truckService.getById(id);
        if (truck != null) {
            ctx.json(ApiResponse.ok(Map.of(
                    "truck", truck,
                    "consignments", truckService.getConsignmentsByTruckId(id),
                    "dispatches", truckService.getDispatchesByTruckId(id)
            )));
        } else {
            ctx.status(404).json(ApiResponse.error("Truck not found"));
        }
    }

    public void create(Context ctx) throws Exception {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String reg = (String) body.get("registrationNumber");
        BigDecimal capacity = new BigDecimal(body.get("capacity").toString());
        String driverName = (String) body.get("driverName");
        String driverLicense = (String) body.get("driverLicense");
        String currentLocation = (String) body.getOrDefault("currentLocation", "");

        try {
            var truck = truckService.create(reg, capacity, driverName, driverLicense, currentLocation);
            ctx.status(201).json(ApiResponse.ok(Map.of("truck", truck)));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(ApiResponse.error(e.getMessage()));
        }
    }

    public void updateStatus(Context ctx) throws Exception {
        UUID id = UUID.fromString(ctx.pathParam("id"));
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String newStatus = (String) body.get("status");
        String note = (String) body.getOrDefault("note", "Status changed to " + newStatus);
        String currentLocation = (String) body.get("currentLocation");
        String destination = (String) body.get("destination");
        UsersRecord user = ctx.attribute("currentUser");

        var truck = truckService.updateStatus(id, newStatus, note, currentLocation, destination, user);
        if (truck != null) {
            ctx.json(ApiResponse.ok(Map.of("truck", truck)));
        } else {
            ctx.status(404).json(ApiResponse.error("Truck not found"));
        }
    }
}
