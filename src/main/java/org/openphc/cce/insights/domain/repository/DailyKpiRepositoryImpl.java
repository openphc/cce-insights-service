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

    // ── active-facility summary ───────────────────────────────────────────────
    // A facility is ACTIVE if it has ≥1 ACCEPTED inbound event (event_time-keyed) today —
    // same "any accepted event" definition as eBuzima Adoption's actual-visits count
    // (mv_daily_adoption_kpis_mv), so a facility with recorded activity is never shown Inactive
    // just because the compliance-matching pipeline hasn't (or never will) match that event to a
    // protocol step. (This previously also required the event to be protocol-MATCHED
    // (compliance_event_logs.processing_status='MATCHED'), on the theory that "active" should mean
    // "contributing to a tracked care journey" — but that made Active/Inactive diverge from Adoption
    // whenever matching lagged or failed, which is confusing and, per RI-62, wrong: connectivity/
    // activity and protocol-tracking are different concepts and shouldn't share one flag.)

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

        // Active Facilities = distinct facilities with ≥1 accepted event today (event_time).
        Long activeFacilities = dsl.select(DSL.field("uniq(facility_id)", Long.class))
            .from(DSL.table(DSL.sql("inbound_event_logs" + finalClause())))
            .where(DSL.field("status").eq("ACCEPTED"))
            .and(DSL.field("facility_id").ne(""))
            .and(DSL.field("facility_id").in(facilityIds))
            .and(DSL.condition("toDate(event_time) = today()"))
            .fetchOne(0, Long.class);

        long active = totalInScope == 0 ? 0L : (activeFacilities == null ? 0L : activeFacilities);
        long inactive = Math.max(0L, totalInScope - active);
        double rate = totalInScope > 0 ? Math.round((double) active / totalInScope * 1000.0) / 10.0 : 0.0;
        return new Object[]{totalInScope, active, inactive, rate};
    }

    // ── active-facility summary, date range ───────────────────────────────────
    // "Active" = facility with ≥1 ACCEPTED event (event_time) anywhere in the range
    // (see getFacilityActivitySummary()).

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

        // Active = distinct facilities with ≥1 accepted event (event_time) in the range.
        Long activeFacilities = dsl.select(DSL.field("uniq(facility_id)", Long.class))
            .from(DSL.table(DSL.sql("inbound_event_logs" + finalClause())))
            .where(DSL.field("status").eq("ACCEPTED"))
            .and(DSL.field("facility_id").ne(""))
            .and(DSL.field("facility_id").in(facilityIds))
            .and(DSL.condition("toDate(event_time) >= ?", startDate))
            .and(DSL.condition("toDate(event_time) <= ?", endDate))
            .fetchOne(0, Long.class);

        long active = totalInScope == 0 ? 0L : (activeFacilities == null ? 0L : activeFacilities);
        long inactive = Math.max(0L, totalInScope - active);
        double rate = totalInScope > 0 ? Math.round((double) active / totalInScope * 1000.0) / 10.0 : 0.0;

        return new Object[]{totalInScope, active, inactive, rate};
    }

    // ── active/inactive facility drill-down detail (per in-scope facility) ─────

    @Override
    public List<Object[]> getFacilityActivityDetail(LocalDate startDate, LocalDate endDate) {
        // ACTIVE = facility with ≥1 ACCEPTED event WITHIN [startDate, endDate] — the summary
        // card's definition (event_time-keyed). This determines active/inactive.
        java.util.Set<String> activeInPeriod = new java.util.HashSet<>();
        dsl.select(DSL.field("facility_id", String.class))
           .from(DSL.table(DSL.sql("inbound_event_logs" + finalClause())))
           .where(DSL.field("status").eq("ACCEPTED"))
           .and(DSL.field("facility_id").ne(""))
           .and(DSL.condition("toDate(event_time) >= ?", startDate))
           .and(DSL.condition("toDate(event_time) <= ?", endDate))
           .groupBy(DSL.field("facility_id"))
           .fetch()
           .forEach(r -> activeInPeriod.add(r.get(0, String.class)));

        // LAST ACTIVITY = most recent event day UP TO the end of the window (no start bound), so an
        // inactive facility that transmitted BEFORE the period still shows its real last-seen date
        // rather than a blank. Only facilities that were never active (up to endDate) stay null.
        java.util.Map<String, String> lastActivityByFacility = new java.util.HashMap<>();
        dsl.select(
                DSL.field("facility_id", String.class),
                DSL.field(DSL.sql("toString(max(toDate(hour)))"), String.class))
           .from(DSL.table("mv_event_volume_hourly"))
           .where(DSL.field("facility_id").ne(""))
           .and(DSL.condition("toDate(hour) <= ?", endDate))
           .groupBy(DSL.field("facility_id"))
           .fetch()
           .forEach(r -> lastActivityByFacility.put(r.get(0, String.class), r.get(1, String.class)));

        // One row per in-scope facility. Java-side combine (rather than a LEFT JOIN) avoids
        // ClickHouse's join-fills-defaults-not-nulls gotcha.
        List<Object[]> out = new java.util.ArrayList<>();
        dsl.select(
                DSL.field("facility_id", String.class),
                DSL.field("facility_name", String.class),
                DSL.field("district_name", String.class))
           .from(DSL.table(DSL.sql("facility" + finalClause())))
           .where(DSL.field("_is_deleted").eq(0))
           .fetch()   // display order (district, facility, last-activity) is applied in the service layer
           .forEach(r -> {
               String fid = r.get(0, String.class);
               out.add(new Object[]{
                   fid,
                   r.get(1, String.class),
                   r.get(2, String.class),
                   lastActivityByFacility.get(fid),   // last-seen day ≤ endDate, or null if never active
                   activeInPeriod.contains(fid) ? 1 : 0
               });
           });
        return out;
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
                    DSL.field("expected_patients_per_day",  Long.class),
                    DSL.field("district_name",              String.class))
                  .from(DSL.table(DSL.sql("facility" + finalClause())))
                  .where(DSL.field("_is_deleted").eq(0))
                  .orderBy(DSL.field("facility_name"))
                  .fetch()
                  .map(r -> new Object[]{
                      r.get(0, String.class),
                      r.get(1, String.class),
                      toLong(r.get(2)),
                      r.get(3, String.class)
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
        // RI-33 period semantics (the service turns these raw inputs into the displayed
        // "Expected Visits (Period)", "Actual Visits (Period)", "Reporting Gap", "Adoption Rate"):
        //   expected baseline/day = max(expected_patients_per_day) from facility
        //   sum_actual            = SUM(mv actual_patients) over snapshot_date in range
        //   expectedVisits        = baseline/day × calendarDays   (derived in the service)
        //   actualVisits          = round(sum_actual)             (period total, not a daily avg)
        //   adoptionRate (%)      = sum_actual / (baseline/day × calendarDays) × 100
        //
        // snapshot_date = toDate(event_time) in the MV, so this is a clinical event-time window.
        // calendarDays (not MV row count) is the denominator so silent days don't over-credit.
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
