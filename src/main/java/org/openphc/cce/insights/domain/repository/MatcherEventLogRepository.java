package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.MatcherEventLog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MatcherEventLogRepository extends ReadOnlyRepository<MatcherEventLog, UUID> {

    List<MatcherEventLog> findBySubjectOrderByEventTimeDesc(String subject);

    List<MatcherEventLog> findByMatcherEventIds(List<UUID> matcherEventIds);

    List<String> findDistinctFacilityIds();

    List<Object[]> findFacilityNames();

    List<String> findDistinctPractitioners();

    /**
     * Raw candidate rows (subject, facility_id, resource data JSON) for every non-duplicate
     * inbound event whose resource type can carry a practitioner reference, within the given
     * range/facility. Callers extract the actual practitioner via {@code PractitionerExtractor}
     * rather than relying on the (currently unpopulated) {@code inbound_event_logs.practitioner_ref}
     * materialized column - see its Javadoc for why.
     */
    List<Object[]> findPractitionerCandidateEvents(OffsetDateTime startDate, OffsetDateTime endDate,
                                                    String facilityId);

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

    List<Object[]> findFacilityEventCounts(UUID protocolDefId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findActivePatientsByFacility(UUID protocolDefId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findFacilityPatientMapping();

    /** (facility_id, patient_id, protocol_instance_id) of the facility's enrolments with enrolled_at within [startDate, endDate]. */
    List<Object[]> findPatientsByFacility(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findPractitionerSummary();

    List<Object[]> findPractitionerSummaryFiltered(OffsetDateTime startDate, OffsetDateTime endDate,
                                                    String facilityId);
}
