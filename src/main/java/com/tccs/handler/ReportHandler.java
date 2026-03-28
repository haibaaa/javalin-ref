package com.tccs.handler;

import com.tccs.dto.ApiResponse;
import io.javalin.http.Context;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tccs.db.Tables.*;

public class ReportHandler {
    private final DSLContext dsl;

    public ReportHandler(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void revenue(Context ctx) {
        LocalDate start = ctx.queryParam("startDate") != null ? LocalDate.parse(ctx.queryParam("startDate")) : LocalDate.now().minusYears(2);
        LocalDate end = ctx.queryParam("endDate") != null ? LocalDate.parse(ctx.queryParam("endDate")) : LocalDate.now();

        var revenueByDest = dsl.select(
                CONSIGNMENTS.DESTINATION,
                DSL.count().as("total_consignments"),
                DSL.sum(CONSIGNMENTS.TRANSPORT_CHARGES).as("total_revenue"),
                DSL.avg(CONSIGNMENTS.TRANSPORT_CHARGES).as("avg_revenue"),
                DSL.sum(CONSIGNMENTS.VOLUME).as("total_volume"),
                DSL.count().filterWhere(CONSIGNMENTS.STATUS.eq("Delivered")).as("delivered_count"),
                DSL.count().filterWhere(CONSIGNMENTS.STATUS.eq("Cancelled")).as("cancelled_count")
        ).from(CONSIGNMENTS)
                .where(DSL.cast(CONSIGNMENTS.REGISTRATION_TIMESTAMP, LocalDate.class).between(start, end))
                .and(CONSIGNMENTS.STATUS.ne("Cancelled"))
                .groupBy(CONSIGNMENTS.DESTINATION)
                .orderBy(DSL.sum(CONSIGNMENTS.TRANSPORT_CHARGES).desc())
                .fetchMaps();

        var daily = dsl.select(
                DSL.cast(CONSIGNMENTS.REGISTRATION_TIMESTAMP, LocalDate.class).as("date"),
                DSL.sum(CONSIGNMENTS.TRANSPORT_CHARGES).as("revenue"),
                DSL.count().as("consignments")
        ).from(CONSIGNMENTS)
                .where(DSL.cast(CONSIGNMENTS.REGISTRATION_TIMESTAMP, LocalDate.class).between(start, end))
                .and(CONSIGNMENTS.STATUS.ne("Cancelled"))
                .groupBy(DSL.cast(CONSIGNMENTS.REGISTRATION_TIMESTAMP, LocalDate.class))
                .orderBy(DSL.field("date").asc())
                .fetchMaps();

        var summaryRow = dsl.select(
                DSL.sum(CONSIGNMENTS.TRANSPORT_CHARGES).as("total_revenue"),
                DSL.count().as("total_consignments"),
                DSL.avg(CONSIGNMENTS.TRANSPORT_CHARGES).as("avg_charge"),
                DSL.sum(CONSIGNMENTS.VOLUME).as("total_volume")
        ).from(CONSIGNMENTS)
                .where(DSL.cast(CONSIGNMENTS.REGISTRATION_TIMESTAMP, LocalDate.class).between(start, end))
                .and(CONSIGNMENTS.STATUS.ne("Cancelled"))
                .fetchOneMap();

        ctx.json(ApiResponse.ok(Map.of(
                "revenueByDestination", revenueByDest,
                "dailyRevenue", daily,
                "summary", summaryRow,
                "dateRange", Map.of("start", start.toString(), "end", end.toString())
        )));
    }

    public void exportCsv(Context ctx) {
        LocalDate start = ctx.queryParam("startDate") != null ? LocalDate.parse(ctx.queryParam("startDate")) : LocalDate.now().minusYears(2);
        LocalDate end = ctx.queryParam("endDate") != null ? LocalDate.parse(ctx.queryParam("endDate")) : LocalDate.now();

        var data = dsl.select(
                CONSIGNMENTS.DESTINATION,
                DSL.count().as("consignments"),
                DSL.sum(CONSIGNMENTS.TRANSPORT_CHARGES).as("revenue"),
                DSL.sum(CONSIGNMENTS.VOLUME).as("volume")
        ).from(CONSIGNMENTS)
                .where(DSL.cast(CONSIGNMENTS.REGISTRATION_TIMESTAMP, LocalDate.class).between(start, end))
                .and(CONSIGNMENTS.STATUS.ne("Cancelled"))
                .groupBy(CONSIGNMENTS.DESTINATION)
                .orderBy(DSL.sum(CONSIGNMENTS.TRANSPORT_CHARGES).desc())
                .fetch();

        StringBuilder csv = new StringBuilder("Destination,Consignments,Revenue,Volume\n");
        for (var row : data) {
            csv.append(row.value1()).append(",")
               .append(row.value2()).append(",")
               .append(row.value3()).append(",")
               .append(row.value4()).append("\n");
        }

        ctx.header("Content-Disposition", "attachment; filename=report.csv");
        ctx.contentType("text/csv");
        ctx.result(csv.toString());
    }
}
