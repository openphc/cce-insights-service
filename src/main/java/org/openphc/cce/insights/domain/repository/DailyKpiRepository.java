package org.openphc.cce.insights.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only access to the refreshable daily KPI materialized views
 * (schema/07-daily-summary-aggregates.sql) and the facility
 * static table (schema/08-reference-tables.sql).
 *
 * jOOQ-generated classes do not yet exist for these tables — all queries
 * use raw DSL.table / DSL.field expressions until codegen is re-run.
 */
public interface DailyKpiRepository {

    /**
     * Active-facility summary for today, derived live from the event_time-keyed
     * mv_event_volume_hourly (a facility is active if it has ≥1 accepted event today).
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
     * Drill-down detail behind the Active/Inactive facility cards: one row per in-scope facility,
     * flagged active/inactive for [startDate, endDate] using the SAME definition as the summary
     * (active = ≥1 ACCEPTED event in mv_event_volume_hourly within the range).
     * last_activity is the most recent event day UP TO endDate (no start bound), so an inactive
     * facility that transmitted before the window still shows its last-seen date; null only if the
     * facility was never active up to endDate.
     * Returns rows of: [facility_id(String), facility_name(String), district_name(String),
     *                   last_activity(String, yyyy-MM-dd or null if never active), active(int 0/1)]
     * Unordered — the service applies the display order (district ↑, facility ↑, last-activity ↓).
     */
    List<Object[]> getFacilityActivityDetail(LocalDate startDate, LocalDate endDate);

    /**
     * mv_daily_adoption_kpis — today's snapshot, one row per facility_id.
     * Returns: [facility_id(String), expected_patients_per_day(long),
     *           sum_actual_patients(double), adoption_rate_pct(double)]
     * Service layer rounds the actual count (ceiling) and derives the reporting gap so
     * the API and UI cannot disagree.
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
     * mv_daily_adoption_kpis — multi-day aggregation for a reporting period.
     * Returns: [facility_id(String), expected_patients_per_day(long),
     *           sum_actual_patients(double), adoption_rate_pct(double)]
     * Service layer ceiling-rounds the daily-average actual and derives the gap.
     */
    List<Object[]> getAdoptionKpisByDateRange(LocalDate startDate, LocalDate endDate);

    // Event-processing KPI reads live in InboundEventRepositoryImpl.eventProcessingKpis now (it reads the
    // redesigned event_time × facility mv_daily_event_kpis).

    // Deviation daily-KPI reads live in DeviationRepositoryImpl now (they read the redesigned
    // occurrence-keyed mv_daily_deviation_kpis directly). The old snapshot-based methods were removed.
}
