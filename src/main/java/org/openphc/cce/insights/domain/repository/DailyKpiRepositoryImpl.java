package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Repository
public class DailyKpiRepositoryImpl implements DailyKpiRepository {

    private final DSLContext dsl;

    @Value("${cce.clickhouse.use-final:false}")
    private boolean useFinal;

    public DailyKpiRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Mirrors AbstractClickHouseRepository.finalClause(). */
    private String finalClause() {
        return useFinal ? " FINAL" : "";
    }

    // ── active-facility summary (live from mv_event_volume_hourly) ────────────
    // Per requirements: a facility is active if it has transmitted ANY successful
    // HIE submission in the period (regardless of compliance match). We therefore
    // count distinct facility_ids in inbound_event_logs (status='ACCEPTED'), not
    // mv_daily_facility_kpis (which only contains compliance-matched events).

    @Override
    public Object[] getFacilityActivitySummary() {
        long totalInScope = toLong(
            dsl.selectCount()
               .from(DSL.table(DSL.sql("facility" + finalClause())))
               .where(DSL.field("_is_deleted").eq(0))
               .fetchOne(0, Long.class));

        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));

        // MV-first: Active Facilities = distinct facilities with clinical activity today, read from
        // the event_time-keyed event-volume MV (mv_event_volume_hourly holds only ACCEPTED events).
        Long activeFacilities = dsl.select(DSL.field("uniq(facility_id)", Long.class))
            .from(DSL.table("mv_event_volume_hourly"))
            .where(DSL.field("facility_id").ne(""))
            .and(DSL.field("facility_id").in(facilityIds))
            .and(DSL.condition("toDate(hour) = today()"))
            .fetchOne(0, Long.class);

        long active = totalInScope == 0 ? 0L : (activeFacilities == null ? 0L : activeFacilities);
        long inactive = Math.max(0L, totalInScope - active);
        double rate = totalInScope > 0 ? Math.round((double) active / totalInScope * 1000.0) / 10.0 : 0.0;
        return new Object[]{totalInScope, active, inactive, rate};
    }

    // ── active-facility summary, date range (live from mv_event_volume_hourly) ─
    // "Active" = facility with ≥1 successful HIE submission anywhere in the range.

    @Override
    public Object[] getFacilityActivitySummaryByDateRange(LocalDate startDate, LocalDate endDate) {
        long totalInScope = toLong(
            dsl.selectCount()
               .from(DSL.table(DSL.sql("facility" + finalClause())))
               .where(DSL.field("_is_deleted").eq(0))
               .fetchOne(0, Long.class));

        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));

        // MV-first: Active = distinct facilities with clinical activity in the range, from the
        // event_time-keyed event-volume MV (see getFacilityActivitySummary()).
        Long activeFacilities = dsl.select(DSL.field("uniq(facility_id)", Long.class))
            .from(DSL.table("mv_event_volume_hourly"))
            .where(DSL.field("facility_id").ne(""))
            .and(DSL.field("facility_id").in(facilityIds))
            .and(DSL.condition("toDate(hour) >= ?", startDate))
            .and(DSL.condition("toDate(hour) <= ?", endDate))
            .fetchOne(0, Long.class);

        long active = totalInScope == 0 ? 0L : (activeFacilities == null ? 0L : activeFacilities);
        long inactive = Math.max(0L, totalInScope - active);
        double rate = totalInScope > 0 ? Math.round((double) active / totalInScope * 1000.0) / 10.0 : 0.0;

        return new Object[]{totalInScope, active, inactive, rate};
    }

    // ── mv_daily_adoption_kpis ───────────────────────────────────────────────

    @Override
    public List<Object[]> getAdoptionKpis() {
        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));
        // Service layer derives actualVisitsPerDay (ceil) + reportingGapPerDay so the
        // API and UI cannot disagree. SQL just returns the raw inputs.
        return dsl.select(
                    DSL.field("facility_id",               String.class),
                    DSL.field(DSL.sql("max(expected_patients_per_day)"), Long.class),
                    DSL.field(DSL.sql("toFloat64(sum(actual_patients))"), Double.class),
                    DSL.field(DSL.sql("if(coalesce(max(expected_patients_per_day), 0) = 0, toFloat64(100.0)," +
                        " coalesce(max(adoption_rate_pct), toFloat64(0)))"), Double.class))
                  .from(DSL.table(DSL.sql("mv_daily_adoption_kpis" + finalClause())))
                  .where(DSL.sql("snapshot_date = today()"))
                  .and(DSL.field("facility_id").in(facilityIds))
                  .groupBy(DSL.field("facility_id"))
                  .fetch()
                  .map(r -> new Object[]{
                      r.get(0, String.class),   // [0] facility_id
                      toLong(r.get(1)),          // [1] expected_patients_per_day
                      toDouble(r.get(2)),        // [2] sum(actual_patients)
                      toDouble(r.get(3))         // [3] adoption_rate_pct
                  });
    }

    // ── facility ─────────────────────────────────────────────────────────────

    @Override
    public List<Object[]> getFacilityReference() {
        // ReplacingMergeTree — FINAL required to see deduplicated view.
        return dsl.select(
                    DSL.field("facility_id",               String.class),
                    DSL.field("facility_name",              String.class),
                    DSL.field("expected_patients_per_day",  Long.class))
                  .from(DSL.table(DSL.sql("facility" + finalClause())))
                  .where(DSL.field("_is_deleted").eq(0))
                  .orderBy(DSL.field("facility_name"))
                  .fetch()
                  .map(r -> new Object[]{
                      r.get(0, String.class),
                      r.get(1, String.class),
                      toLong(r.get(2))
                  });
    }

    // ── mv_daily_compliance_kpis (all protocols) ─────────────────────────────

    @Override
    public Object[] getComplianceKpisAll(LocalDate snapshotDate) {
        var dateFilter = snapshotDate != null
                ? DSL.field("snapshot_date", LocalDate.class).eq(snapshotDate)
                : DSL.condition("snapshot_date = today()");
        var row = dsl.select(
                    DSL.sum(DSL.field("step_completed",              Long.class)),
                    DSL.sum(DSL.field("step_overdue",                Long.class)),
                    DSL.sum(DSL.field("step_missed",                 Long.class)),
                    DSL.sum(DSL.field("step_due",                    Long.class)),
                    DSL.sum(DSL.field("step_pending",                Long.class)),
                    DSL.sum(DSL.field("step_early",                  Long.class)),
                    DSL.sum(DSL.field("step_on_time",                Long.class)),
                    DSL.sum(DSL.field("step_late",                   Long.class)),
                    DSL.sum(DSL.field("step_total",                  Long.class)),
                    DSL.sum(DSL.field("total_enrollments",           Long.class)),
                    DSL.sum(DSL.field("compliant_count",             Long.class)),
                    DSL.sum(DSL.field("total_deviations",            Long.class)),
                    DSL.sum(DSL.field("overdue_deviations",          Long.class)),
                    DSL.sum(DSL.field("missed_deviations",           Long.class)),
                    DSL.sum(DSL.field("order_violation_deviations",  Long.class)))
                  .from(DSL.table(DSL.sql("mv_daily_compliance_kpis" + finalClause())))
                  .where(dateFilter)
                  .fetchOne();
        if (row == null) return new Object[15];
        return new Object[]{
            toLong(row.get(0)),  toLong(row.get(1)),  toLong(row.get(2)),
            toLong(row.get(3)),  toLong(row.get(4)),  toLong(row.get(5)),
            toLong(row.get(6)),  toLong(row.get(7)),  toLong(row.get(8)),
            toLong(row.get(9)),  toLong(row.get(10)), toLong(row.get(11)),
            toLong(row.get(12)), toLong(row.get(13)), toLong(row.get(14))
        };
    }

    // ── mv_daily_compliance_kpis (single protocol) ───────────────────────────
    // MV stores one row per (protocol_definition_id, facility_id, snapshot_date).
    // Must SUM across facilities — fetchOne() throws TooManyRowsException when >1 facility exists.

    @Override
    public Object[] getComplianceKpisByProtocol(UUID protocolDefinitionId, LocalDate snapshotDate) {
        var dateFilter = snapshotDate != null
                ? DSL.field("snapshot_date", LocalDate.class).eq(snapshotDate)
                : DSL.condition("snapshot_date = today()");
        var row = dsl.select(
                    DSL.sum(DSL.field("step_completed",              Long.class)),
                    DSL.sum(DSL.field("step_overdue",                Long.class)),
                    DSL.sum(DSL.field("step_missed",                 Long.class)),
                    DSL.sum(DSL.field("step_due",                    Long.class)),
                    DSL.sum(DSL.field("step_pending",                Long.class)),
                    DSL.sum(DSL.field("step_early",                  Long.class)),
                    DSL.sum(DSL.field("step_on_time",                Long.class)),
                    DSL.sum(DSL.field("step_late",                   Long.class)),
                    DSL.sum(DSL.field("step_total",                  Long.class)),
                    DSL.sum(DSL.field("total_enrollments",           Long.class)),
                    DSL.sum(DSL.field("compliant_count",             Long.class)),
                    DSL.sum(DSL.field("total_deviations",            Long.class)),
                    DSL.sum(DSL.field("overdue_deviations",          Long.class)),
                    DSL.sum(DSL.field("missed_deviations",           Long.class)),
                    DSL.sum(DSL.field("order_violation_deviations",  Long.class)),
                    DSL.sum(DSL.field("status_active",               Long.class)),
                    DSL.sum(DSL.field("status_completed",            Long.class)),
                    DSL.sum(DSL.field("status_withdrawn",            Long.class)),
                    DSL.sum(DSL.field("status_expired",              Long.class)))
                  .from(DSL.table(DSL.sql("mv_daily_compliance_kpis" + finalClause())))
                  .where(dateFilter)
                  .and(DSL.field("protocol_definition_id").eq(protocolDefinitionId.toString()))
                  .fetchOne();
        if (row == null) return new Object[19];
        return new Object[]{
            toLong(row.get(0)),  toLong(row.get(1)),  toLong(row.get(2)),  toLong(row.get(3)),
            toLong(row.get(4)),  toLong(row.get(5)),  toLong(row.get(6)),  toLong(row.get(7)),
            toLong(row.get(8)),  toLong(row.get(9)),  toLong(row.get(10)), toLong(row.get(11)),
            toLong(row.get(12)), toLong(row.get(13)), toLong(row.get(14)),
            toLong(row.get(15)), toLong(row.get(16)), toLong(row.get(17)), toLong(row.get(18))
        };
    }

    // ── mv_daily_adoption_kpis (date range) ──────────────────────────────────

    @Override
    public List<Object[]> getAdoptionKpisByDateRange(LocalDate startDate, LocalDate endDate) {
        // Period semantics, all aligned with UI labels "Expected Visits / Day", "Actual
        // Visits / Day", "Reporting Gap / Day":
        //   expectedVisitsPerDay = baseline from facility (per day)
        //   actualVisitsPerDay   = AVG of daily reporters over the calendar range
        //                          (sum of MV actual_patients ÷ calendar_days)
        //   reportingGapPerDay   = expectedVisitsPerDay − actualVisitsPerDay
        //   adoptionRate (%)     = total_actual / (expected × calendar_days) × 100
        //
        // Calendar days (not MV row count) is used so that a facility with sparse MV
        // data is not double-credited — silent days contribute zero to the daily average.
        long calendarDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));

        String rateExpr =
            "toFloat32(round(if(coalesce(max(expected_patients_per_day), 0) = 0, 100.0," +
            " sum(actual_patients) / nullIf(coalesce(max(expected_patients_per_day), 0) * " + calendarDays + ", 0) * 100), 1))";

        // Service layer derives ceiled actualVisitsPerDay + reportingGapPerDay so the
        // table columns reconcile by construction. SQL only returns raw inputs + the
        // precise period adoption rate (which doesn't suffer from rounding artefacts).
        return dsl.select(
                    DSL.field("facility_id",              String.class),
                    DSL.field(DSL.sql("max(expected_patients_per_day)"), Long.class),
                    DSL.field(DSL.sql("toFloat64(sum(actual_patients))"), Double.class),
                    DSL.field(DSL.sql(rateExpr), Double.class))
                  .from(DSL.table(DSL.sql("mv_daily_adoption_kpis" + finalClause())))
                  .where(DSL.field("snapshot_date", LocalDate.class).between(startDate).and(endDate))
                  .and(DSL.field("facility_id").in(facilityIds))
                  .groupBy(DSL.field("facility_id"))
                  .fetch()
                  .map(r -> new Object[]{
                      r.get(0, String.class),   // [0] facility_id
                      toLong(r.get(1)),          // [1] expected_visits_per_day
                      toDouble(r.get(2)),        // [2] sum(actual_patients) — raw
                      toDouble(r.get(3))         // [3] adoption_rate_pct (period)
                  });
    }

    // mv_daily_event_kpis reads moved to InboundEventRepositoryImpl.eventProcessingKpis (it reads the
    // redesigned event_time × facility MV). The old snapshot getEventKpis here was removed.

    // NOTE: the deviation daily-KPI reads moved to DeviationRepositoryImpl (countByTypeFiltered /
    // findDeviationTrends / findDeviationsByAction), which read the redesigned occurrence-keyed
    // mv_daily_deviation_kpis directly. The old snapshot-based getDeviationKpis* here were dead.

    // ── helpers ──────────────────────────────────────────────────────────────

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Double d) return d;
        if (v instanceof Number n) return Math.round(n.doubleValue() * 10.0) / 10.0;
        return 0.0;
    }
}
