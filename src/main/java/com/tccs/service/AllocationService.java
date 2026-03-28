package com.tccs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tccs.config.AppConfig;
import com.tccs.db.tables.records.ConsignmentsRecord;
import com.tccs.db.tables.records.TrucksRecord;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static com.tccs.db.Tables.*;

public class AllocationService {
    private final DSLContext dsl;
    private final ObjectMapper mapper;
    private final double allocationThreshold;

    public record AllocationResult(
            boolean triggered, String reason, BigDecimal totalVolume,
            Map<String, Object> truckInfo, String destination,
            int consignmentCount, List<String> consignments, boolean noTrucks
    ) {}

    public AllocationService(DSLContext dsl, ObjectMapper mapper, AppConfig config) {
        this.dsl = dsl;
        this.mapper = mapper;
        this.allocationThreshold = config.getDouble("tccs.allocation.threshold", 500.0);
    }

    public AllocationResult checkAndTriggerAllocation(String destination) throws Exception {
        BigDecimal totalVolume = dsl.select(DSL.sum(CONSIGNMENTS.VOLUME))
                .from(CONSIGNMENTS)
                .where(CONSIGNMENTS.DESTINATION.eq(destination))
                .and(CONSIGNMENTS.STATUS.in("Registered", "Pending"))
                .fetchOneInto(BigDecimal.class);
        
        if (totalVolume == null) totalVolume = BigDecimal.ZERO;
        double threshold = allocationThreshold;

        if (totalVolume.doubleValue() < threshold) {
            // Mark registered ones as pending
            var registeredOnes = dsl.selectFrom(CONSIGNMENTS)
                    .where(CONSIGNMENTS.DESTINATION.eq(destination))
                    .and(CONSIGNMENTS.STATUS.eq("Registered"))
                    .fetch();
            
            for (ConsignmentsRecord c : registeredOnes) {
                appendStatusLog(c, c.getStatus(), "Pending",
                        String.format("Awaiting volume threshold (%.2fm³ / %.0fm³)",
                                totalVolume.doubleValue(), threshold));
                c.setStatus("Pending");
                c.store();
            }
            return new AllocationResult(false,
                    String.format("Volume %.2fm³ < %.0fm³ threshold", totalVolume.doubleValue(), threshold),
                    totalVolume, null, destination, 0, null, false);
        }

        // Find suitable truck
        var trucks = dsl.selectFrom(TRUCKS)
                .where(TRUCKS.STATUS.eq("Available"))
                .and(TRUCKS.CAPACITY.ge(totalVolume))
                .fetch();
        
        if (trucks.isEmpty()) {
            trucks = dsl.selectFrom(TRUCKS).where(TRUCKS.STATUS.eq("Available")).fetch();
        }
        
        if (trucks.isEmpty()) {
            return new AllocationResult(false, "No available trucks", totalVolume,
                    null, destination, 0, null, true);
        }

        TrucksRecord truck = trucks.get(0);
        var pending = dsl.selectFrom(CONSIGNMENTS)
                .where(CONSIGNMENTS.DESTINATION.eq(destination))
                .and(CONSIGNMENTS.STATUS.in("Registered", "Pending"))
                .fetch();

        List<String> consignmentNumbers = new ArrayList<>();
        for (ConsignmentsRecord c : pending) {
            appendStatusLog(c, c.getStatus(), "AllocatedToTruck",
                    "Allocated to truck " + truck.getRegistrationNumber());
            c.setStatus("AllocatedToTruck");
            c.setAssignedTruckId(truck.getTruckId());
            c.store();
            consignmentNumbers.add(c.getConsignmentNumber());
        }

        // Update truck
        appendTruckStatusLog(truck, "Allocated",
                String.format("Allocated for %s with %d consignments (%.2fm³)",
                        destination, pending.size(), totalVolume.doubleValue()));
        truck.setStatus("Allocated");
        truck.setDestination(destination);
        truck.setCargoVolume(totalVolume);
        truck.store();

        Map<String, Object> truckInfo = Map.of(
                "id", truck.getTruckId().toString(),
                "registrationNumber", truck.getRegistrationNumber(),
                "driverName", truck.getDriverName()
        );

        return new AllocationResult(true, "Allocation successful", totalVolume,
                truckInfo, destination, pending.size(), consignmentNumbers, false);
    }

    public List<Map<String, Object>> getPendingVolumes() {
        var rows = dsl.select(CONSIGNMENTS.DESTINATION, DSL.sum(CONSIGNMENTS.VOLUME), DSL.count())
                .from(CONSIGNMENTS)
                .where(CONSIGNMENTS.STATUS.in("Registered", "Pending"))
                .groupBy(CONSIGNMENTS.DESTINATION)
                .fetch();
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : rows) {
            String dest = row.value1();
            BigDecimal vol = row.value2();
            int count = row.value3();
            double pct = vol.doubleValue() / allocationThreshold * 100;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("destination", dest);
            item.put("pendingVolume", vol);
            item.put("consignmentCount", count);
            item.put("thresholdPercentage", Math.round(pct * 10.0) / 10.0);
            item.put("threshold", allocationThreshold);
            item.put("nearingThreshold", pct >= 80);
            result.add(item);
        }
        return result;
    }

    private void appendStatusLog(ConsignmentsRecord c, String oldStatus, String newStatus, String note) {
        try {
            List<Map<String, Object>> log = mapper.readValue(c.getStatusChangeLog().data(), new TypeReference<>() {});
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("oldStatus", oldStatus);
            entry.put("newStatus", newStatus);
            entry.put("timestamp", OffsetDateTime.now().toString());
            entry.put("note", note);
            log.add(entry);
            c.setStatusChangeLog(JSON.json(mapper.writeValueAsString(log)));
        } catch (Exception ignored) {}
    }

    private void appendTruckStatusLog(TrucksRecord truck, String newStatus, String note) {
        try {
            List<Map<String, Object>> log = mapper.readValue(truck.getStatusHistory().data(), new TypeReference<>() {});
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("status", newStatus);
            entry.put("timestamp", OffsetDateTime.now().toString());
            entry.put("note", note);
            log.add(entry);
            truck.setStatusHistory(JSON.json(mapper.writeValueAsString(log)));
        } catch (Exception ignored) {}
    }
}
