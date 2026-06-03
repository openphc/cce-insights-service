package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.EventLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EventLogRepository extends ReadOnlyRepository<EventLog, UUID> {

    List<EventLog> findBySubjectOrderByEventTimeDesc(String subject);

    @Query(value = "SELECT DISTINCT el.facility_id FROM event_log el " +
            "WHERE el.facility_id IS NOT NULL ORDER BY el.facility_id",
            nativeQuery = true)
    List<String> findDistinctFacilityIds();

    @Query(value = "SELECT DISTINCT el.facility_id, " +
            "el.data->'location'->0->'location'->>'display' AS facility_name " +
            "FROM event_log el " +
            "WHERE el.type = 'Encounter' " +
            "AND el.data->'location'->0->'location'->>'display' IS NOT NULL",
            nativeQuery = true)
    List<Object[]> findFacilityNames();

    @Query(value = "SELECT DISTINCT COALESCE(" +
            "el.data->'participant'->0->'individual'->>'reference', " +
            "el.data->'performer'->0->>'reference', " +
            "el.data->'asserter'->>'reference', " +
            "el.data->'requester'->>'reference', " +
            "el.data->'performer'->0->'actor'->>'reference'" +
            ") AS practitioner_ref FROM event_log el " +
            "WHERE COALESCE(" +
            "el.data->'participant'->0->'individual'->>'reference', " +
            "el.data->'performer'->0->>'reference', " +
            "el.data->'asserter'->>'reference', " +
            "el.data->'requester'->>'reference', " +
            "el.data->'performer'->0->'actor'->>'reference'" +
            ") IS NOT NULL " +
            "AND COALESCE(" +
            "el.data->'participant'->0->'individual'->>'reference', " +
            "el.data->'performer'->0->>'reference', " +
            "el.data->'asserter'->>'reference', " +
            "el.data->'requester'->>'reference', " +
            "el.data->'performer'->0->'actor'->>'reference'" +
            ") LIKE 'Practitioner/%' " +
            "ORDER BY practitioner_ref",
            nativeQuery = true)
    List<String> findDistinctPractitioners();

    @Query(value = "SELECT el.data->>'resourceType' AS resource_type, COUNT(*) AS event_count " +
            "FROM event_log el " +
            "WHERE el.processing_status != 'DUPLICATE' " +
            "AND (CAST(:facilityId AS text) IS NULL OR el.facility_id = :facilityId) " +
            "AND (CAST(:source AS text) IS NULL OR el.source = :source) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR el.event_time >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR el.event_time <= :endDate) " +
            "GROUP BY el.data->>'resourceType' ORDER BY event_count DESC",
            nativeQuery = true)
    List<Object[]> countByResourceType(@Param("facilityId") String facilityId,
                                       @Param("source") String source,
                                       @Param("startDate") OffsetDateTime startDate,
                                       @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT el.facility_id, el.data->>'resourceType' AS resource_type, COUNT(*) AS event_count " +
            "FROM event_log el " +
            "WHERE el.facility_id IS NOT NULL " +
            "AND el.processing_status != 'DUPLICATE' " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR el.event_time >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR el.event_time <= :endDate) " +
            "GROUP BY el.facility_id, el.data->>'resourceType' " +
            "ORDER BY el.facility_id, event_count DESC",
            nativeQuery = true)
    List<Object[]> countByFacility(@Param("startDate") OffsetDateTime startDate,
                                   @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT " +
            "COALESCE(" +
            "  el.data->'participant'->0->'individual'->>'reference', " +
            "  el.data->'performer'->0->>'reference', " +
            "  el.data->'asserter'->>'reference', " +
            "  el.data->'requester'->>'reference', " +
            "  el.data->'performer'->0->'actor'->>'reference'" +
            ") AS practitioner_ref, " +
            "COALESCE(" +
            "  el.data->'participant'->0->'individual'->>'display', " +
            "  el.data->'performer'->0->>'display', " +
            "  el.data->'asserter'->>'display', " +
            "  el.data->'requester'->>'display', " +
            "  el.data->'performer'->0->'actor'->>'display'" +
            ") AS practitioner_display, " +
            "el.data->>'resourceType' AS resource_type, COUNT(*) AS event_count " +
            "FROM event_log el " +
            "WHERE el.processing_status != 'DUPLICATE' " +
            "AND (CAST(:facilityId AS text) IS NULL OR el.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR el.event_time >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR el.event_time <= :endDate) " +
            "GROUP BY practitioner_ref, practitioner_display, resource_type " +
            "HAVING COALESCE(" +
            "  el.data->'participant'->0->'individual'->>'reference', " +
            "  el.data->'performer'->0->>'reference', " +
            "  el.data->'asserter'->>'reference', " +
            "  el.data->'requester'->>'reference', " +
            "  el.data->'performer'->0->'actor'->>'reference'" +
            ") IS NOT NULL " +
            "AND COALESCE(" +
            "  el.data->'participant'->0->'individual'->>'reference', " +
            "  el.data->'performer'->0->>'reference', " +
            "  el.data->'asserter'->>'reference', " +
            "  el.data->'requester'->>'reference', " +
            "  el.data->'performer'->0->'actor'->>'reference'" +
            ") LIKE 'Practitioner/%' " +
            "ORDER BY event_count DESC",
            nativeQuery = true)
    List<Object[]> countByPractitioner(@Param("facilityId") String facilityId,
                                       @Param("startDate") OffsetDateTime startDate,
                                       @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT el.source, el.data->>'resourceType' AS resource_type, COUNT(*) AS event_count " +
            "FROM event_log el " +
            "WHERE el.processing_status != 'DUPLICATE' " +
            "AND (CAST(:facilityId AS text) IS NULL OR el.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR el.event_time >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR el.event_time <= :endDate) " +
            "GROUP BY el.source, el.data->>'resourceType' " +
            "ORDER BY el.source, event_count DESC",
            nativeQuery = true)
    List<Object[]> countBySource(@Param("facilityId") String facilityId,
                                 @Param("startDate") OffsetDateTime startDate,
                                 @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT DATE_TRUNC(:interval, el.event_time) AS period, " +
            "el.data->>'resourceType' AS resource_type, COUNT(*) AS event_count " +
            "FROM event_log el " +
            "WHERE el.processing_status != 'DUPLICATE' " +
            "AND (CAST(:facilityId AS text) IS NULL OR el.facility_id = :facilityId) " +
            "AND (CAST(:source AS text) IS NULL OR el.source = :source) " +
            "AND (CAST(:resourceType AS text) IS NULL OR el.data->>'resourceType' = :resourceType) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR el.event_time >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR el.event_time <= :endDate) " +
            "GROUP BY period, el.data->>'resourceType' ORDER BY period",
            nativeQuery = true)
    List<Object[]> findEventTrends(@Param("interval") String interval,
                                   @Param("facilityId") String facilityId,
                                   @Param("source") String source,
                                   @Param("resourceType") String resourceType,
                                   @Param("startDate") OffsetDateTime startDate,
                                   @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT el.processing_status, COUNT(*) AS count " +
            "FROM event_log el " +
            "WHERE (CAST(:facilityId AS text) IS NULL OR el.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR el.event_time >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR el.event_time <= :endDate) " +
            "GROUP BY el.processing_status",
            nativeQuery = true)
    List<Object[]> countByProcessingStatus(@Param("facilityId") String facilityId,
                                           @Param("startDate") OffsetDateTime startDate,
                                           @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT el.source, el.processing_status, COUNT(*) AS count " +
            "FROM event_log el " +
            "WHERE (CAST(:source AS text) IS NULL OR el.source = :source) " +
            "AND (CAST(:facilityId AS text) IS NULL OR el.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR el.event_time >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR el.event_time <= :endDate) " +
            "GROUP BY el.source, el.processing_status ORDER BY el.source",
            nativeQuery = true)
    List<Object[]> findProcessingQualityBySource(@Param("source") String source,
                                                  @Param("facilityId") String facilityId,
                                                  @Param("startDate") OffsetDateTime startDate,
                                                  @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT el.facility_id, " +
            "COUNT(DISTINCT pi.id) AS total_enrollments, " +
            "COUNT(DISTINCT el.id) AS total_events " +
            "FROM event_log el " +
            "JOIN protocol_instance pi ON el.protocol_instance_id = pi.id " +
            "WHERE el.facility_id IS NOT NULL " +
            "AND (CAST(:protocolDefId AS uuid) IS NULL OR pi.protocol_definition_id = :protocolDefId) " +
            "GROUP BY el.facility_id",
            nativeQuery = true)
    List<Object[]> findFacilityEventCounts(@Param("protocolDefId") UUID protocolDefId);

    @Query(value = "SELECT el.facility_id, " +
            "COUNT(DISTINCT pi.patient_id) AS total_patients " +
            "FROM protocol_instance pi " +
            "JOIN event_log el ON el.protocol_instance_id = pi.id " +
            "WHERE pi.status = 'ACTIVE' AND el.facility_id IS NOT NULL " +
            "AND (CAST(:protocolDefId AS uuid) IS NULL OR pi.protocol_definition_id = :protocolDefId) " +
            "GROUP BY el.facility_id",
            nativeQuery = true)
    List<Object[]> findActivePatientsByFacility(@Param("protocolDefId") UUID protocolDefId);

    @Query(value = "SELECT DISTINCT el.facility_id, pi.patient_id " +
            "FROM event_log el " +
            "JOIN protocol_instance pi ON el.protocol_instance_id = pi.id " +
            "WHERE el.facility_id IS NOT NULL",
            nativeQuery = true)
    List<Object[]> findFacilityPatientMapping();

    @Query(value = "SELECT DISTINCT el.facility_id, pi.patient_id, pi.id AS protocol_instance_id " +
            "FROM event_log el " +
            "JOIN protocol_instance pi ON el.protocol_instance_id = pi.id " +
            "WHERE el.facility_id IS NOT NULL " +
            "AND el.facility_id = CAST(:facilityId AS text)",
            nativeQuery = true)
    List<Object[]> findPatientsByFacility(@Param("facilityId") String facilityId);

    @Query(value = "SELECT practitioner_ref, practitioner_display, el.facility_id, " +
            "COUNT(DISTINCT el.id) AS total_events, " +
            "COUNT(DISTINCT pi.patient_id) AS total_patients " +
            "FROM event_log el " +
            "JOIN protocol_instance pi ON el.protocol_instance_id = pi.id " +
            "CROSS JOIN LATERAL ( " +
            "  SELECT COALESCE(" +
            "    el.data->'participant'->0->'individual'->>'reference', " +
            "    el.data->'performer'->0->>'reference', " +
            "    el.data->'asserter'->>'reference', " +
            "    el.data->'requester'->>'reference'" +
            "  ) AS practitioner_ref, " +
            "  COALESCE(" +
            "    el.data->'participant'->0->'individual'->>'display', " +
            "    el.data->'performer'->0->>'display', " +
            "    el.data->'asserter'->>'display', " +
            "    el.data->'requester'->>'display'" +
            "  ) AS practitioner_display " +
            ") pr " +
            "WHERE pr.practitioner_ref IS NOT NULL " +
            "AND pr.practitioner_ref LIKE 'Practitioner/%' " +
            "AND el.processing_status != 'DUPLICATE' " +
            "GROUP BY practitioner_ref, practitioner_display, el.facility_id " +
            "ORDER BY total_events DESC",
            nativeQuery = true)
    List<Object[]> findPractitionerSummary();

    @Query(value = "SELECT practitioner_ref, practitioner_display, el.facility_id, " +
            "COUNT(DISTINCT el.id) AS total_events, " +
            "COUNT(DISTINCT pi.patient_id) AS total_patients " +
            "FROM event_log el " +
            "JOIN protocol_instance pi ON el.protocol_instance_id = pi.id " +
            "CROSS JOIN LATERAL ( " +
            "  SELECT COALESCE(" +
            "    el.data->'participant'->0->'individual'->>'reference', " +
            "    el.data->'performer'->0->>'reference', " +
            "    el.data->'asserter'->>'reference', " +
            "    el.data->'requester'->>'reference'" +
            "  ) AS practitioner_ref, " +
            "  COALESCE(" +
            "    el.data->'participant'->0->'individual'->>'display', " +
            "    el.data->'performer'->0->>'display', " +
            "    el.data->'asserter'->>'display', " +
            "    el.data->'requester'->>'display'" +
            "  ) AS practitioner_display " +
            ") pr " +
            "WHERE pr.practitioner_ref IS NOT NULL " +
            "AND pr.practitioner_ref LIKE 'Practitioner/%' " +
            "AND el.processing_status != 'DUPLICATE' " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR el.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR el.received_at <= :endDate) " +
            "AND (CAST(:facilityId AS text) IS NULL OR el.facility_id = :facilityId) " +
            "GROUP BY practitioner_ref, practitioner_display, el.facility_id " +
            "ORDER BY total_events DESC",
            nativeQuery = true)
    List<Object[]> findPractitionerSummaryFiltered(@Param("startDate") OffsetDateTime startDate,
                                                    @Param("endDate") OffsetDateTime endDate,
                                                    @Param("facilityId") String facilityId);
}
