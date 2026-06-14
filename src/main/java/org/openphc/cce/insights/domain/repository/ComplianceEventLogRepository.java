package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ComplianceEventLog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ComplianceEventLogRepository extends ReadOnlyRepository<ComplianceEventLog, UUID> {

    List<ComplianceEventLog> findBySubjectOrderByEventTimeDesc(String subject);

    List<ComplianceEventLog> findByComplianceEventIds(List<UUID> complianceEventIds);

    List<String> findDistinctFacilityIds();

    List<Object[]> findFacilityNames();

    List<String> findDistinctPractitioners();

    List<Object[]> countByResourceType(String facilityId, String source,
                                       OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByFacility(OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByPractitioner(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countBySource(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                   String resourceType, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByProcessingStatus(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findProcessingQualityBySource(String source, String facilityId,
                                                  OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findFacilityEventCounts(UUID protocolDefId);

    List<Object[]> findActivePatientsByFacility(UUID protocolDefId);

    List<Object[]> findFacilityPatientMapping();

    List<Object[]> findPatientsByFacility(String facilityId);

    List<Object[]> findPractitionerSummary();

    List<Object[]> findPractitionerSummaryFiltered(OffsetDateTime startDate, OffsetDateTime endDate,
                                                    String facilityId);
}
