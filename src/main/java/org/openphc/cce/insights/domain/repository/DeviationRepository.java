package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.enums.DeviationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviationRepository extends ReadOnlyRepository<Deviation, UUID> {

    List<Deviation> findByProtocolInstanceId(UUID protocolInstanceId);

    Page<Deviation> findByDeviationType(DeviationType type, Pageable pageable);

    List<Object[]> findFilteredDeviations(String deviationType, String facilityId, String district,
                                          UUID protocolDefinitionId,
                                          OffsetDateTime startDate, OffsetDateTime endDate, int lim);

    List<Object[]> findDeviationTrends(String interval, OffsetDateTime startDate,
                                       OffsetDateTime endDate, String facilityId, String district,
                                       UUID protocolDefinitionId);

    List<Object[]> findDeviationsByAction(UUID protocolDefId, String facilityId, String district, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findResolutionRate(UUID protocolDefId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByTypeSince(OffsetDateTime since, String facilityId);

    List<Object[]> countByTypeInRange(OffsetDateTime startDate, OffsetDateTime endDate, String facilityId);

    /**
     * Period count of deviations grouped by deviation_type, with optional protocol and facility filters.
     * Replaces summing daily snapshot rows from mv_daily_deviation_kpis (which double-counts across days).
     * Returns rows of [deviation_type(String), count(long)].
     */
    List<Object[]> countByTypeFiltered(UUID protocolDefinitionId, String facilityId, String district,
                                        OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findRepeatDeviationPatients(int minDeviations, String facilityId,
                                               OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countDeviationsByFacility();

    /**
     * Deviations DETECTED within [startDate, endDate] per facility, attributed via the patient's
     * current facility (mv_patient_facility_latest). Null bounds are open (all-time). Returns rows of
     * [facility_id(String), deviation_count(long)]. Used to date-scope the Facility Ranking /
     * Top-Bottom deviation column (the MV's total_deviations is all-time cumulative, not windowed).
     */
    List<Object[]> countDeviationsByFacility(OffsetDateTime startDate, OffsetDateTime endDate);

    long countDistinctPatientsWithDeviations();

    /**
     * Distinct patients who (a) enrolled within [startDate, endDate] AND (b) have at least one
     * deviation whose CLINICAL OCCURRENCE date (occurredAt(), not detected_at) falls in that window.
     * This is the "non-compliant" cohort count for the compliance summary. Null bounds open.
     */
    long countDistinctPatientsWithDeviationsBetween(OffsetDateTime startDate, OffsetDateTime endDate);

    /** Facility-scoped variant of {@link #countDistinctPatientsWithDeviationsBetween} — patients
     *  attributed to {@code facilityId} via mv_patient_facility_latest. */
    long countDistinctPatientsWithDeviationsBetween(String facilityId,
                                                    OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * RI-36 non-compliant — of the tracked matched-event cohort (patients with a protocol-MATCHED
     * event in range; see {@link InboundEventRepository#countDistinctPatientsWithMatchedEvents}),
     * how many have a deviation whose clinical OCCURRENCE date is in [start,end] (not detected_at,
     * no enrolled_at). Facility ({@code null}/'' = all); optional dates.
     */
    long countDistinctNonCompliantAmongMatched(String facilityId, String district,
                                               OffsetDateTime startDate, OffsetDateTime endDate);

    // Batch load full Deviation objects for a set of protocol instances
    List<Deviation> findByProtocolInstanceIdIn(List<UUID> ids);

    // Batch count — returns [protocolInstanceId, count] per instance; replaces per-instance calls.
    // When startDate/endDate are set, counts only deviations whose CLINICAL OCCURRENCE date
    // (occurredAt(), not system detected_at) falls in range — matches the Deviations page.
    List<Object[]> countDeviationsByProtocolInstanceIdIn(List<UUID> ids,
                                                         OffsetDateTime startDate,
                                                         OffsetDateTime endDate);

    // Returns one row per deviation [protocolInstanceId(UUID), deviationType(String)] for the given
    // instances, filtered by CLINICAL OCCURRENCE date in [startDate, endDate] (occurredAt(), not
    // detected_at). Lets the caller derive the per-instance compliance split AND the by-type
    // breakdown on the clinical clock, consistent with mv_daily_deviation_kpis / the Deviations page.
    List<Object[]> findDeviationTypesByInstanceIdIn(List<UUID> ids,
                                                    OffsetDateTime startDate,
                                                    OffsetDateTime endDate);

    // Returns one row per protocol: [protocolDefinitionId, totalDeviations]
    List<Object[]> findDeviationCountsByFacilityGroupedByProtocol(String facilityId);

    // Returns single row: [compliantPatients, totalDeviations, overdueDevs, missedDevs, orderViolationDevs]
    Object[] aggregateDeviationMetrics(UUID protocolDefinitionId);

    Object[] aggregateDeviationMetricsAll();

    Object[] aggregateDeviationMetricsByFacility(String facilityId);

    Object[] aggregateDeviationMetricsByProtocolAndFacility(UUID protocolDefinitionId, String facilityId);

    /**
     * RI-34 — deviations DETECTED within [startDate, endDate], grouped by facility and broken down
     * by type, for the "Deviations by Facility and Type" chart. Same raw-table attribution as
     * {@link #countDeviationsByFacility(OffsetDateTime, OffsetDateTime)} (via mv_patient_facility_latest),
     * NOT mv_daily_deviation_kpis — summing that snapshot MV across days double-counts deviations
     * (esp. OVERDUE, which re-appears in each day's snapshot until resolved); the raw deviations
     * table has no such risk. {@code sortBy} selects which count ranks the page — one of
     * "total_deviations" (default), "overdue_count", "missed_count", "order_violation_count",
     * matching the chart's type-filter toggle (selecting a single type re-ranks by that type,
     * not the total). Returns rows of
     * [facility_id(String), overdue(long), missed(long), orderViolation(long), total(long)].
     */
    List<Object[]> findDeviationsByFacilityAndType(String facilityId, String district, UUID protocolDefinitionId,
                                                   OffsetDateTime startDate, OffsetDateTime endDate,
                                                   String sortBy, int limit, int offset);

    /** Count of distinct facilities with at least one matching deviation — pagination total for
     *  {@link #findDeviationsByFacilityAndType}. */
    long countFacilitiesWithDeviations(String facilityId, String district, UUID protocolDefinitionId,
                                       OffsetDateTime startDate, OffsetDateTime endDate);
}
