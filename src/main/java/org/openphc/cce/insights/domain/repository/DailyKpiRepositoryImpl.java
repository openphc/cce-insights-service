package org.openphc.cce.insights.domain.repository;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    // ── mv_daily_facility_activity_summary ───────────────────────────────────

    @Override
    public Object[] getFacilityActivitySummary() {
        // facility is the source of truth — total, active, and inactive all anchored to it.
        long totalInScope = toLong(
            dsl.selectCount()
               .from(DSL.table(DSL.sql("facility" + finalClause())))
               .where(DSL.field("_is_deleted").eq(0))
               .fetchOne(0, Long.class));

        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));

        Long activeFacilities = dsl.selectCount()
            .from(
                dsl.select(DSL.field("facility_id"))
                   .from(DSL.table(DSL.sql("mv_daily_facility_kpis" + finalClause())))
                   .where(DSL.sql("snapshot_date = today()"))
                   .and(DSL.field("facility_id").in(facilityIds))
                   .groupBy(DSL.field("facility_id"))
                   .having(DSL.condition(DSL.sql("sum(event_count) > 0")))
                   .asTable("active_fac")
            )
            .fetchOne(0, Long.class);

        long active = totalInScope == 0 ? 0L : (activeFacilities == null ? 0L : activeFacilities);
        long inactive = Math.max(0L, totalInScope - active);
        double rate = totalInScope > 0 ? Math.round((double) active / totalInScope * 1000.0) / 10.0 : 0.0;
        return new Object[]{totalInScope, active, inactive, rate};
    }

    // ── mv_daily_facility_activity_summary (date range) ─────────────────────
    // "Active" = had event_count > 0 on at least one day in the range.
    // total_in_scope always comes from facility (programme list).

    @Override
    public Object[] getFacilityActivitySummaryByDateRange(LocalDate startDate, LocalDate endDate) {
        long totalInScope = toLong(
            dsl.selectCount()
               .from(DSL.table(DSL.sql("facility" + finalClause())))
               .fetchOne(0, Long.class));

        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));

        Long activeFacilities = dsl.selectCount()
            .from(
                dsl.select(DSL.field("facility_id"))
                   .from(DSL.table(DSL.sql("mv_daily_facility_kpis" + finalClause())))
                   .where(DSL.field("snapshot_date", LocalDate.class).between(startDate).and(endDate))
                   .and(DSL.field("facility_id").in(facilityIds))
                   .groupBy(DSL.field("facility_id"))
                   .having(DSL.condition(DSL.sql("sum(event_count) > 0")))
                   .asTable("active_fac")
            )
            .fetchOne(0, Long.class);

        long active = totalInScope == 0 ? 0L : (activeFacilities == null ? 0L : activeFacilities);
        long inactive = Math.max(0L, totalInScope - active);
        double rate = totalInScope > 0 ? Math.round((double) active / totalInScope * 1000.0) / 10.0 : 0.0;

        return new Object[]{totalInScope, active, inactive, rate};
    }

    // ── mv_daily_facility_kpis ───────────────────────────────────────────────

    @Override
    public List<Object[]> getFacilityKpis() {
        // MV has one row per (facility_id, protocol_definition_id, snapshot_date).
        // GROUP BY facility_id to collapse multi-protocol rows into a single per-facility result.
        String complianceExpr =
            "toFloat64(round(sum(compliant_patients) / nullIf(sum(tracked_patients), 0) * 100, 1))";
        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));
        return dsl.select(
                    DSL.field("facility_id",                               String.class),
                    DSL.sum(DSL.field("tracked_patients",       Long.class)),
                    DSL.sum(DSL.field("compliant_patients",     Long.class)),
                    DSL.sum(DSL.field("non_compliant_patients", Long.class)),
                    DSL.field(DSL.sql(complianceExpr)),
                    DSL.sum(DSL.field("total_deviations",       Long.class)),
                    DSL.sum(DSL.field("event_count",            Long.class)))
                  .from(DSL.table(DSL.sql("mv_daily_facility_kpis" + finalClause())))
                  .where(DSL.sql("snapshot_date = today()"))
                  .and(DSL.field("facility_id").in(facilityIds))
                  .groupBy(DSL.field("facility_id"))
                  .orderBy(DSL.field(DSL.sql(complianceExpr)).desc())
                  .fetch()
                  .map(r -> new Object[]{
                      r.get(0, String.class),
                      toLong(r.get(1)),
                      toLong(r.get(2)),
                      toLong(r.get(3)),
                      toDouble(r.get(4)),
                      toLong(r.get(5)),
                      toLong(r.get(6))
                  });
    }

    // ── mv_daily_adoption_kpis ───────────────────────────────────────────────

    @Override
    public List<Object[]> getAdoptionKpis() {
        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));
        return dsl.select(
                    DSL.field("facility_id",               String.class),
                    DSL.field("facility_name",              String.class),
                    DSL.field(DSL.sql("max(expected_patients_per_day)"), Long.class),
                    DSL.sum(DSL.field("actual_patients", Long.class)),
                    DSL.field(DSL.sql("if(max(expected_patients_per_day) = 0, toFloat64(100.0)," +
                        " max(adoption_rate_pct))"), Double.class),
                    DSL.field(DSL.sql("if(max(expected_patients_per_day) = 0, toInt64(0)," +
                        " max(reporting_gap))"), Long.class))
                  .from(DSL.table(DSL.sql("mv_daily_adoption_kpis" + finalClause())))
                  .where(DSL.sql("snapshot_date = today()"))
                  .and(DSL.field("facility_id").in(facilityIds))
                  .groupBy(DSL.field("facility_id"), DSL.field("facility_name"))
                  .orderBy(DSL.field(DSL.sql("max(reporting_gap)")).desc())
                  .fetch()
                  .map(r -> new Object[]{
                      r.get(0, String.class),
                      r.get(1, String.class),
                      toLong(r.get(2)),
                      toLong(r.get(3)),
                      toDouble(r.get(4)),
                      toLong(r.get(5))
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

    // ── mv_daily_facility_kpis (date range) ──────────────────────────────────

    @Override
    public List<Object[]> getFacilityKpisByDateRange(LocalDate startDate, LocalDate endDate) {
        // Compliance fields: argMax over snapshot_date = state at the latest snapshot in range.
        // event_count: SUM = total events transmitted across all days in the range.
        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));
        return dsl.select(
                    DSL.field("facility_id", String.class),
                    DSL.field(DSL.sql("argMax(tracked_patients,       snapshot_date)"), Long.class),
                    DSL.field(DSL.sql("argMax(compliant_patients,     snapshot_date)"), Long.class),
                    DSL.field(DSL.sql("argMax(non_compliant_patients, snapshot_date)"), Long.class),
                    DSL.field(DSL.sql("argMax(compliance_rate_pct,    snapshot_date)"), Double.class),
                    DSL.field(DSL.sql("argMax(total_deviations,       snapshot_date)"), Long.class),
                    DSL.sum(DSL.field("event_count", Long.class)))
                  .from(DSL.table(DSL.sql("mv_daily_facility_kpis" + finalClause())))
                  .where(DSL.field("snapshot_date", LocalDate.class).between(startDate).and(endDate))
                  .and(DSL.field("facility_id").in(facilityIds))
                  .groupBy(DSL.field("facility_id"))
                  .orderBy(DSL.field(DSL.sql("argMax(compliance_rate_pct, snapshot_date)")).desc())
                  .fetch()
                  .map(r -> new Object[]{
                      r.get(0, String.class),
                      toLong(r.get(1)),
                      toLong(r.get(2)),
                      toLong(r.get(3)),
                      toDouble(r.get(4)),
                      toLong(r.get(5)),
                      toLong(r.get(6))
                  });
    }

    // ── mv_daily_adoption_kpis (date range) ──────────────────────────────────

    @Override
    public List<Object[]> getAdoptionKpisByDateRange(LocalDate startDate, LocalDate endDate) {
        // Per schema/07: total_actual = SUM(actual_patients),
        // total_expected = expected_patients_per_day × count(distinct snapshot_date),
        // period_rate    = total_actual / total_expected × 100.
        // count() after FINAL = distinct days with MV data (not full calendar range).
        // Group by (facility_id, facility_name) only — max(expected_patients_per_day) collapses
        // historical 0-value rows that appear when facility was updated after MV population.
        // countIf(expected_patients_per_day > 0) counts only days with a valid baseline.
        var facilityIds = dsl.select(DSL.field("facility_id"))
                             .from(DSL.table(DSL.sql("facility" + finalClause())))
                             .where(DSL.field("_is_deleted").eq(0));
        return dsl.select(
                    DSL.field("facility_id",              String.class),
                    DSL.field("facility_name",             String.class),
                    DSL.field(DSL.sql("max(expected_patients_per_day)"), Long.class),
                    DSL.sum(DSL.field("actual_patients",  Long.class)),
                    DSL.field(DSL.sql(
                        "toFloat32(round(if(max(expected_patients_per_day) = 0, 100.0," +
                        "  sum(actual_patients) / nullIf(max(expected_patients_per_day) *" +
                        "  countIf(expected_patients_per_day > 0), 0) * 100), 1))")),
                    DSL.field(DSL.sql(
                        "toInt64(if(max(expected_patients_per_day) = 0, 0," +
                        "  max(expected_patients_per_day) * countIf(expected_patients_per_day > 0)" +
                        "  - sum(actual_patients)))")))
                  .from(DSL.table(DSL.sql("mv_daily_adoption_kpis" + finalClause())))
                  .where(DSL.field("snapshot_date", LocalDate.class).between(startDate).and(endDate))
                  .and(DSL.field("facility_id").in(facilityIds))
                  .groupBy(
                      DSL.field("facility_id"),
                      DSL.field("facility_name"))
                  .orderBy(DSL.field(DSL.sql(
                      "max(expected_patients_per_day) * countIf(expected_patients_per_day > 0)" +
                      " - sum(actual_patients)")).desc())
                  .fetch()
                  .map(r -> new Object[]{
                      r.get(0, String.class),
                      r.get(1, String.class),
                      toLong(r.get(2)),
                      toLong(r.get(3)),
                      toDouble(r.get(4)),
                      toLong(r.get(5))
                  });
    }

    // ── mv_daily_event_kpis ──────────────────────────────────────────────────

    @Override
    public Object[] getEventKpis() {
        var row = dsl.select(
                    DSL.field("total_events",        Long.class),
                    DSL.field("matched_count",       Long.class),
                    DSL.field("zero_match_count",    Long.class),
                    DSL.field("duplicate_count",     Long.class),
                    DSL.field("matched_rate_pct",    Double.class),
                    DSL.field("zero_match_rate_pct", Double.class),
                    DSL.field("pipeline_loss_count", Long.class))
                  .from(DSL.table(DSL.sql("mv_daily_event_kpis" + finalClause())))
                  .where(DSL.sql("snapshot_date = today()"))
                  .limit(1)
                  .fetchOne();
        if (row == null) return new Object[]{0L, 0L, 0L, 0L, 0.0, 0.0, 0L};
        return new Object[]{
            toLong(row.get(0)),   toLong(row.get(1)),   toLong(row.get(2)),   toLong(row.get(3)),
            toDouble(row.get(4)), toDouble(row.get(5)), toLong(row.get(6))
        };
    }

    // ── mv_daily_deviation_kpis ───────────────────────────────────────────────

    @Override
    public Object[] getDeviationKpis(UUID protocolDefinitionId) {
        var where = DSL.condition("snapshot_date = today()");
        if (protocolDefinitionId != null) {
            where = where.and(DSL.condition(
                    "protocol_definition_id = toUUID(?)", protocolDefinitionId.toString()));
        }
        var row = dsl.select(
                    DSL.sum(DSL.field("total_deviations",      Long.class)),
                    DSL.sum(DSL.field("overdue_count",         Long.class)),
                    DSL.sum(DSL.field("missed_count",          Long.class)),
                    DSL.sum(DSL.field("order_violation_count", Long.class)))
                  .from(DSL.table(DSL.sql("mv_daily_deviation_kpis" + finalClause())))
                  .where(where)
                  .fetchOne();
        if (row == null) return new Object[]{0L, 0L, 0L, 0L};
        return new Object[]{
            toLong(row.get(0)), toLong(row.get(1)),
            toLong(row.get(2)), toLong(row.get(3))
        };
    }

    @Override
    @Deprecated
    public Object[] getDeviationKpisAll() {
        return getDeviationKpis(null);
    }

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
