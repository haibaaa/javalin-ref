package com.tccs.handler;

import com.tccs.db.tables.records.UsersRecord;
import com.tccs.dto.ApiResponse;
import com.tccs.service.ConsignmentService;
import com.tccs.service.TruckService;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class ConsignmentHandler {
    private final ConsignmentService consignmentService;
    private final TruckService truckService;

    public ConsignmentHandler(ConsignmentService consignmentService, TruckService truckService) {
        this.consignmentService = consignmentService;
        this.truckService = truckService;
    }

    public void getAll(Context ctx) {
        String status = ctx.queryParam("status");
        String destination = ctx.queryParam("destination");
        String search = ctx.queryParam("search");
        String startDate = ctx.queryParam("startDate");
        String endDate = ctx.queryParam("endDate");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

        var list = consignmentService.getAll(status, destination, search, startDate, endDate, limit, offset);
        int total = consignmentService.countAll(status, destination, search, startDate, endDate);

        ctx.json(ApiResponse.ok(Map.of(
                "consignments", list,
                "total", total,
                "limit", limit,
                "offset", offset
        )));
    }

    public void create(Context ctx) throws Exception {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        BigDecimal volume = new BigDecimal(body.get("volume").toString());
        String destination = (String) body.get("destination");
        String senderAddress = (String) body.get("senderAddress");
        String receiverAddress = (String) body.get("receiverAddress");
        UsersRecord user = ctx.attribute("currentUser");

        var result = consignmentService.create(volume, destination, senderAddress, receiverAddress, user);
        ctx.status(201).json(ApiResponse.ok(Map.of(
                "consignment", result.consignment(),
                "bill", result.bill().bill(),
                "pricingBreakdown", result.bill().pricingBreakdown(),
                "allocationTriggered", result.allocation().triggered(),
                "allocationDetails", result.allocation()
        )));
    }

    public void getById(Context ctx) {
        String id = ctx.pathParam("id");
        var c = consignmentService.getById(id);
        if (c != null) {
            var truckInfo = c.getAssignedTruckId() != null ? truckService.getById(c.getAssignedTruckId()) : null;
            ctx.json(ApiResponse.ok(Map.of(
                    "consignment", c,
                    "truck", truckInfo
            )));
        } else {
            ctx.status(404).json(ApiResponse.error("Consignment not found"));
        }
    }

    public void updateStatus(Context ctx) throws Exception {
        String id = ctx.pathParam("id");
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String newStatus = body.get("status");
        String note = body.getOrDefault("note", "Status updated to " + newStatus);
        UsersRecord user = ctx.attribute("currentUser");

        consignmentService.updateStatus(id, newStatus, note, user);
        ctx.json(ApiResponse.ok(Map.of("message", "Status updated")));
    }
}
