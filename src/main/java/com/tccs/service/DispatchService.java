package com.tccs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tccs.db.tables.records.ConsignmentsRecord;
import com.tccs.db.tables.records.DispatchDocumentsRecord;
import com.tccs.db.tables.records.TrucksRecord;
import com.tccs.db.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.jooq.JSON;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static com.tccs.db.Tables.*;

public class DispatchService {
    private final DSLContext dsl;
    private final ObjectMapper mapper;
    private final TruckService truckService;
    private final ConsignmentService consignmentService;

    public DispatchService(DSLContext dsl, ObjectMapper mapper, TruckService truckService, ConsignmentService consignmentService) {
        this.dsl = dsl;
        this.mapper = mapper;
        this.truckService = truckService;
        this.consignmentService = consignmentService;
    }

    public List<DispatchDocumentsRecord> getAll(String status, String destination) {
        var query = dsl.selectFrom(DISPATCH_DOCUMENTS);
        if (status != null) query.where(DISPATCH_DOCUMENTS.DISPATCH_STATUS.eq(status));
        if (destination != null) query.where(DISPATCH_DOCUMENTS.DESTINATION.containsIgnoreCase(destination));
        return query.orderBy(DISPATCH_DOCUMENTS.DISPATCH_TIMESTAMP.desc()).fetch();
    }

    public DispatchDocumentsRecord create(UUID truckId, OffsetDateTime departureTime, UsersRecord user) throws Exception {
        TrucksRecord truck = truckService.getById(truckId);
        if (truck == null) throw new RuntimeException("Truck not found");

        if (!"Allocated".equals(truck.getStatus()) && !"Loading".equals(truck.getStatus())) {
            throw new RuntimeException("Truck must be in Allocated or Loading status");
        }

        var consignments = dsl.selectFrom(CONSIGNMENTS)
                .where(CONSIGNMENTS.ASSIGNED_TRUCK_ID.eq(truckId))
                .and(CONSIGNMENTS.STATUS.eq("AllocatedToTruck"))
                .fetch();
        
        if (consignments.isEmpty()) throw new RuntimeException("No consignments allocated to this truck");

        BigDecimal totalVolume = consignments.stream()
                .map(ConsignmentsRecord::getVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> manifest = consignments.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("consignmentNumber", c.getConsignmentNumber());
            item.put("volume", c.getVolume());
            item.put("senderAddress", c.getSenderAddress());
            item.put("receiverAddress", c.getReceiverAddress());
            item.put("charges", c.getTransportCharges());
            return item;
        }).collect(java.util.stream.Collectors.toList());

        DispatchDocumentsRecord dispatch = dsl.newRecord(DISPATCH_DOCUMENTS);
        dispatch.setDispatchId(UUID.randomUUID());
        dispatch.setTruckId(truckId);
        dispatch.setDestination(truck.getDestination());
        dispatch.setDispatchTimestamp(OffsetDateTime.now());
        dispatch.setTotalConsignments(consignments.size());
        dispatch.setTotalVolume(totalVolume);
        dispatch.setDriverName(truck.getDriverName());
        dispatch.setDepartureTime(departureTime != null ? departureTime : OffsetDateTime.now());
        dispatch.setDispatchStatus("Dispatched");
        dispatch.setConsignmentManifest(JSON.json(mapper.writeValueAsString(manifest)));
        dispatch.setCreatedBy(user != null ? user.getUserId() : null);
        dispatch.store();

        // Update truck to InTransit
        truckService.updateStatus(truckId, "InTransit", "Dispatch generated", null, null, user);

        // Update consignments to InTransit
        for (ConsignmentsRecord c : consignments) {
            consignmentService.updateStatus(c.getConsignmentNumber(), "InTransit", "Truck dispatched - ID: " + dispatch.getDispatchId(), user);
        }

        return dispatch;
    }

    public DispatchDocumentsRecord getById(UUID id) {
        return dsl.selectFrom(DISPATCH_DOCUMENTS).where(DISPATCH_DOCUMENTS.DISPATCH_ID.eq(id)).fetchOne();
    }
}
