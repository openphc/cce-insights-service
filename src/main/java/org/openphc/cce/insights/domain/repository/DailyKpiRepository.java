package org.openphc.cce.insights.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only access to the six refreshable daily KPI materialized views
 * (schema/07-daily-summary-aggregates.sql) and the facility
 * static table (schema/08-reference-tables.sql).
 *
 * jOOQ-generated classes do not yet exist for these tables — all queries
 * use raw DSL.table / DSL.field expressions until codegen is re-run.
 */
public interface DailyKpiRepository {

    /**
     * mv_daily_facility_activity_summary — single row (today's snapshot).
     * Returns: [total_in_scope(long), active_facilities(long),
     *           inactive_facilities(long), active_facility_rate_pct(double)]
     */
    Object[] getFacilityActivitySummary();

    /**
     * Date-range-aware version: counts facilities that transmitted ≥1 event
     * on at least one day within [startDate, endDate].
     * Returns same indices as {@link #getFacilityActivitySummary()}.
     */
    Object[] getFacilityActivitySummaryByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * mv_daily_facility_kpis — one row per facility_id.
     * Returns: [facility_id(String), tracked_patients(long), compliant_patients(long),
     *           non_compliant_patients(long), compliance_rate_pct(double),
     *           total_deviations(long), event_count(long)]
     */
    List<Object[]> getFacilityKpis();

    /**
     * mv_daily_adoption_kpis — one row per facility_id, ordered by reporting_gap DESC.
     * Returns: [facility_id(String), facility_name(String),
     *           expected_patients_per_day(long), actual_patients(long),
     *           adoption_rate_pct(double), reporting_gap(long)]
     */
    List<Object[]> getAdoptionKpis();

    /**
     * facility FINAL — one row per facility_id, ordered by facility_name.
     * Returns: [facility_id(String), facility_name(String), expected_patients_per_day(long)]
     */
    List<Object[]> getFacilityReference();

    /**
     * mv_daily_compliance_kpis — SUMs across ALL protocols for a specific snapshot day.
     * Pass {@code null} to use today's snapshot.
     * Returns: [0] step_completed, [1] step_overdue, [2] step_missed, [3] step_due,
     *          [4] step_pending, [5] step_early, [6] step_on_time, [7] step_late,
     *          [8] step_total, [9] total_enrollments, [10] compliant_count,
     *          [11] total_deviations, [12] overdue_deviations,
     *          [13] missed_deviations, [14] order_violation_deviations
     */
    Object[] getComplianceKpisAll(LocalDate snapshotDate);

    /**
     * mv_daily_compliance_kpis — single row for the given protocol on a specific snapshot day.
     * Pass {@code null} snapshotDate to use today's snapshot.
     * Indices [0..14] same as getComplianceKpisAll(); additionally:
     *          [15] status_active, [16] status_completed,
     *          [17] status_withdrawn, [18] status_expired
     * Returns null-filled array if no row exists.
     */
    Object[] getComplianceKpisByProtocol(UUID protocolDefinitionId, LocalDate snapshotDate);

    /**
     * mv_daily_facility_kpis — date-range-aware version.
     * Returns one row per facility aggregated over [startDate, endDate]:
     *   tracked/compliant/non_compliant = SUM of daily totals (protocols collapsed per day)
     *   compliance_rate_pct             = recomputed from those period totals
     *   total_deviations                = latest snapshot in the range (argMax)
     *   event_count                     = SUM of daily events over the range
     * Same column indices as {@link #getFacilityKpis()}.
     */
    List<Object[]> getFacilityKpisByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * mv_daily_adoption_kpis — multi-day aggregation for a reporting period.
     * Per schema/07 formula: total_actual = SUM(actual_patients),
     * total_expected = expected_patients_per_day × days_in_period,
     * period_rate    = total_actual / total_expected × 100.
     * Returns: [0] facility_id, [1] facility_name, [2] expected_patients_per_day,
     *          [3] total_actual, [4] adoption_rate_pct (period), [5] reporting_gap (period)
     */
    List<Object[]> getAdoptionKpisByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * mv_daily_event_kpis — single row (total event processing summary, all time).
     * Returns: [0] total_events, [1] matched_count, [2] zero_match_count,
     *          [3] duplicate_count, [4] matched_rate_pct, [5] zero_match_rate_pct,
     *          [6] pipeline_loss_count
     */
    Object[] getEventKpis();

    /**
     * mv_daily_deviation_kpis — filtered by optional protocol (null = all protocols).
     * Returns: [0] total_deviations, [1] overdue_count,
     *          [2] missed_count, [3] order_violation_count
     */
    Object[] getDeviationKpis(UUID protocolDefinitionId);

    /**
     * mv_daily_deviation_kpis — aggregated over [startDate, endDate].
     * Returns same indices as {@link #getDeviationKpis(UUID)}.
     */
    Object[] getDeviationKpisByDateRange(UUID protocolDefinitionId, LocalDate startDate, LocalDate endDate);

    /** @deprecated use {@link #getDeviationKpis(UUID)} with null */
    @Deprecated
    Object[] getDeviationKpisAll();
}
