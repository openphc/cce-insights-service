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

    List<Object[]> findFilteredDeviations(String deviationType, String facilityId,
                                          UUID protocolDefinitionId,
                                          OffsetDateTime startDate, OffsetDateTime endDate, int lim);

    List<Object[]> findDeviationTrends(String interval, OffsetDateTime startDate,
                                       OffsetDateTime endDate, String facilityId,
                                       UUID protocolDefinitionId);

    List<Object[]> findDeviationsByAction(UUID protocolDefId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findResolutionRate(UUID protocolDefId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByTypeSince(OffsetDateTime since, String facilityId);

    List<Object[]> countByTypeInRange(OffsetDateTime startDate, OffsetDateTime endDate, String facilityId);

    /**
     * Period count of deviations grouped by deviation_type, with optional protocol and facility filters.
     * Replaces summing daily snapshot rows from mv_daily_deviation_kpis (which double-counts across days).
     * Returns rows of [deviation_type(String), count(long)].
     */
    List<Object[]> countByTypeFiltered(UUID protocolDefinitionId, String facilityId,
                                        OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findRepeatDeviationPatients(int minDeviations, String facilityId,
                                               OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countDeviationsByFacility();

    long countDistinctPatientsWithDeviations();

    /** Distinct patients with at least one deviation detected within [startDate, endDate]. */
    long countDistinctPatientsWithDeviationsBetween(OffsetDateTime startDate, OffsetDateTime endDate);

    // Batch load full Deviation objects for a set of protocol instances
    List<Deviation> findByProtocolInstanceIdIn(List<UUID> ids);

    // Batch count — returns [protocolInstanceId, count] per instance; replaces per-instance calls.
    // When startDate/endDate are set, counts only deviations with detected_at in range (matches dashboard).
    List<Object[]> countDeviationsByProtocolInstanceIdIn(List<UUID> ids,
                                                         OffsetDateTime startDate,
                                                         OffsetDateTime endDate);

    // Returns one row per protocol: [protocolDefinitionId, totalDeviations]
    List<Object[]> findDeviationCountsByFacilityGroupedByProtocol(String facilityId);

    // Returns single row: [compliantPatients, totalDeviations, overdueDevs, missedDevs, orderViolationDevs]
    Object[] aggregateDeviationMetrics(UUID protocolDefinitionId);

    Object[] aggregateDeviationMetricsAll();

    Object[] aggregateDeviationMetricsByFacility(String facilityId);

    Object[] aggregateDeviationMetricsByProtocolAndFacility(UUID protocolDefinitionId, String facilityId);
}
