package com.tccs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tccs.db.tables.records.ConsignmentsRecord;
import com.tccs.db.tables.records.UsersRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.tccs.db.Tables.*;

public class ConsignmentService {
    private final DSLContext dsl;
    private final ObjectMapper mapper;
    private final BillService billService;
    private final AllocationService allocationService;

    public record ConsignmentCreateResult(ConsignmentsRecord consignment, BillService.BillResult bill, AllocationService.AllocationResult allocation) {}

    public ConsignmentService(DSLContext dsl, ObjectMapper mapper, BillService billService, AllocationService allocationService) {
        this.dsl = dsl;
        this.mapper = mapper;
        this.billService = billService;
        this.allocationService = allocationService;
    }

    public List<ConsignmentsRecord> getAll(String status, String destination, String search, String startDate, String endDate, int limit, int offset) {
        Condition cond = DSL.noCondition();
        if (status != null) cond = cond.and(CONSIGNMENTS.STATUS.eq(status));
        if (destination != null) cond = cond.and(CONSIGNMENTS.DESTINATION.containsIgnoreCase(destination));
        if (search != null) {
            cond = cond.and(CONSIGNMENTS.CONSIGNMENT_NUMBER.containsIgnoreCase(search)
                    .or(CONSIGNMENTS.SENDER_ADDRESS.containsIgnoreCase(search))
                    .or(CONSIGNMENTS.RECEIVER_ADDRESS.containsIgnoreCase(search)));
        }
        if (startDate != null) cond = cond.and(CONSIGNMENTS.REGISTRATION_TIMESTAMP.ge(OffsetDateTime.parse(startDate + "T00:00:00Z")));
        if (endDate != null) cond = cond.and(CONSIGNMENTS.REGISTRATION_TIMESTAMP.le(OffsetDateTime.parse(endDate + "T23:59:59Z")));

        return dsl.selectFrom(CONSIGNMENTS)
                .where(cond)
                .orderBy(CONSIGNMENTS.REGISTRATION_TIMESTAMP.desc())
                .limit(limit)
                .offset(offset)
                .fetch();
    }

    public int countAll(String status, String destination, String search, String startDate, String endDate) {
        Condition cond = DSL.noCondition();
        if (status != null) cond = cond.and(CONSIGNMENTS.STATUS.eq(status));
        if (destination != null) cond = cond.and(CONSIGNMENTS.DESTINATION.containsIgnoreCase(destination));
        if (search != null) {
            cond = cond.and(CONSIGNMENTS.CONSIGNMENT_NUMBER.containsIgnoreCase(search)
                    .or(CONSIGNMENTS.SENDER_ADDRESS.containsIgnoreCase(search))
                    .or(CONSIGNMENTS.RECEIVER_ADDRESS.containsIgnoreCase(search)));
        }
        if (startDate != null) cond = cond.and(CONSIGNMENTS.REGISTRATION_TIMESTAMP.ge(OffsetDateTime.parse(startDate + "T00:00:00Z")));
        if (endDate != null) cond = cond.and(CONSIGNMENTS.REGISTRATION_TIMESTAMP.le(OffsetDateTime.parse(endDate + "T23:59:59Z")));

        return dsl.fetchCount(CONSIGNMENTS, cond);
    }

    public ConsignmentCreateResult create(BigDecimal volume, String destination, String senderAddress, String receiverAddress, UsersRecord user) throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        
        long todayCount = dsl.fetchCount(CONSIGNMENTS, CONSIGNMENTS.REGISTRATION_TIMESTAMP.ge(startOfDay));
        String consignmentNumber = String.format("TCCS-%s-%04d",
                now.format(DateTimeFormatter.ofPattern("yyyyMMdd")), todayCount + 1);

        List<Map<String, Object>> log = List.of(Map.of(
                "oldStatus", "", // Use empty string instead of null for simpler parsing if needed
                "newStatus", "Registered",
                "timestamp", now.toString(),
                "note", "Consignment registered"
        ));

        ConsignmentsRecord c = dsl.newRecord(CONSIGNMENTS);
        c.setConsignmentNumber(consignmentNumber);
        c.setVolume(volume);
        c.setDestination(destination);
        c.setSenderAddress(senderAddress);
        c.setReceiverAddress(receiverAddress);
        c.setRegistrationTimestamp(now);
        c.setStatus("Registered");
        c.setStatusChangeLog(JSON.json(mapper.writeValueAsString(log)));
        c.setCreatedBy(user != null ? user.getUserId() : null);
        c.store();

        var billResult = billService.generateBill(consignmentNumber, volume, destination, now);
        var allocationResult = allocationService.checkAndTriggerAllocation(destination);

        // Refresh record to get updated charges
        c.refresh();
        return new ConsignmentCreateResult(c, billResult, allocationResult);
    }

    public ConsignmentsRecord getById(String id) {
        return dsl.selectFrom(CONSIGNMENTS).where(CONSIGNMENTS.CONSIGNMENT_NUMBER.eq(id)).fetchOne();
    }

    public void updateStatus(String id, String newStatus, String note, UsersRecord user) throws Exception {
        ConsignmentsRecord c = getById(id);
        if (c == null) return;

        List<Map<String, Object>> log = mapper.readValue(c.getStatusChangeLog().data(), new TypeReference<>() {});
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("oldStatus", c.getStatus());
        entry.put("newStatus", newStatus);
        entry.put("timestamp", OffsetDateTime.now().toString());
        entry.put("note", note);
        if (user != null) entry.put("updatedBy", user.getName());
        log.add(entry);

        c.setStatus(newStatus);
        c.setStatusChangeLog(JSON.json(mapper.writeValueAsString(log)));
        c.store();
    }
}
