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
                                          OffsetDateTime startDate, OffsetDateTime endDate, int lim);

    List<Object[]> findDeviationTrends(String interval, OffsetDateTime startDate,
                                       OffsetDateTime endDate, String facilityId, String actionId);

    List<Object[]> findDeviationsByAction(UUID protocolDefId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findResolutionRate(UUID protocolDefId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByTypeSince(OffsetDateTime since);

    List<Object[]> countByTypeInRange(OffsetDateTime startDate, OffsetDateTime endDate, String facilityId);

    List<Object[]> findRepeatDeviationPatients(int minDeviations, String facilityId,
                                               OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countDeviationsByFacility();

    long countDistinctPatientsWithDeviations();

    // Batch load full Deviation objects for a set of protocol instances
    List<Deviation> findByProtocolInstanceIdIn(List<UUID> ids);

    // Batch count — returns [protocolInstanceId, count] per instance; replaces per-instance calls
    List<Object[]> countDeviationsByProtocolInstanceIdIn(List<UUID> ids);

    // Returns one row per protocol: [protocolDefinitionId, totalDeviations]
    List<Object[]> findDeviationCountsByFacilityGroupedByProtocol(String facilityId);

    // Returns single row: [compliantPatients, totalDeviations, overdueDevs, missedDevs, orderViolationDevs]
    Object[] aggregateDeviationMetrics(UUID protocolDefinitionId);

    Object[] aggregateDeviationMetricsAll();

    Object[] aggregateDeviationMetricsByFacility(String facilityId);

    Object[] aggregateDeviationMetricsByProtocolAndFacility(UUID protocolDefinitionId, String facilityId);
}
