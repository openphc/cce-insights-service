package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.InboundEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface InboundEventRepository extends ReadOnlyRepository<InboundEvent, UUID> {

    @Query(value = "SELECT DISTINCT ie.source FROM inbound_event ie " +
            "WHERE ie.source IS NOT NULL ORDER BY ie.source",
            nativeQuery = true)
    List<String> findDistinctSources();

    // --- Ingestion Funnel ---

    @Query(value = "SELECT ie.status, COUNT(*) AS event_count " +
            "FROM inbound_event ie " +
            "WHERE (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:source AS text) IS NULL OR ie.source = :source) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate) " +
            "GROUP BY ie.status ORDER BY event_count DESC",
            nativeQuery = true)
    List<Object[]> countByStatus(@Param("facilityId") String facilityId,
                                  @Param("source") String source,
                                  @Param("startDate") OffsetDateTime startDate,
                                  @Param("endDate") OffsetDateTime endDate);

    // --- Rejection Analytics ---

    @Query(value = "SELECT ie.rejection_reason, COUNT(*) AS event_count " +
            "FROM inbound_event ie " +
            "WHERE ie.status = 'REJECTED' " +
            "AND (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:source AS text) IS NULL OR ie.source = :source) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate) " +
            "GROUP BY ie.rejection_reason ORDER BY event_count DESC",
            nativeQuery = true)
    List<Object[]> countByRejectionReason(@Param("facilityId") String facilityId,
                                           @Param("source") String source,
                                           @Param("startDate") OffsetDateTime startDate,
                                           @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT DATE_TRUNC(:interval, ie.received_at) AS period, ie.status, COUNT(*) AS event_count " +
            "FROM inbound_event ie " +
            "WHERE (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:source AS text) IS NULL OR ie.source = :source) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate) " +
            "GROUP BY period, ie.status ORDER BY period",
            nativeQuery = true)
    List<Object[]> findIngestionTrends(@Param("interval") String interval,
                                        @Param("facilityId") String facilityId,
                                        @Param("source") String source,
                                        @Param("startDate") OffsetDateTime startDate,
                                        @Param("endDate") OffsetDateTime endDate);

    // --- Source Data Quality ---

    @Query(value = "SELECT ie.source, ie.status, COUNT(*) AS event_count " +
            "FROM inbound_event ie " +
            "WHERE (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate) " +
            "GROUP BY ie.source, ie.status ORDER BY ie.source",
            nativeQuery = true)
    List<Object[]> countBySourceAndStatus(@Param("facilityId") String facilityId,
                                           @Param("startDate") OffsetDateTime startDate,
                                           @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT ie.source, ie.rejection_reason, COUNT(*) AS event_count " +
            "FROM inbound_event ie " +
            "WHERE ie.status = 'REJECTED' " +
            "AND (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate) " +
            "GROUP BY ie.source, ie.rejection_reason ORDER BY ie.source, event_count DESC",
            nativeQuery = true)
    List<Object[]> countBySourceAndRejectionReason(@Param("facilityId") String facilityId,
                                                    @Param("startDate") OffsetDateTime startDate,
                                                    @Param("endDate") OffsetDateTime endDate);

    // --- Pipeline Loss Detection ---
    // Events ACCEPTED by collector but absent from event_log (lost in Kafka or compliance processing)

    @Query(value = "SELECT ie.source, COUNT(*) AS lost_count " +
            "FROM inbound_event ie " +
            "WHERE ie.status = 'ACCEPTED' " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM event_log el " +
            "  WHERE el.cloudevents_id = ie.cloudevents_id " +
            "  AND el.source = ie.source" +
            ") " +
            "AND (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate) " +
            "GROUP BY ie.source ORDER BY lost_count DESC",
            nativeQuery = true)
    List<Object[]> findPipelineLossBySource(@Param("facilityId") String facilityId,
                                             @Param("startDate") OffsetDateTime startDate,
                                             @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT COUNT(*) FROM inbound_event ie " +
            "WHERE ie.status = 'ACCEPTED' " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM event_log el " +
            "  WHERE el.cloudevents_id = ie.cloudevents_id " +
            "  AND el.source = ie.source" +
            ") " +
            "AND (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate)",
            nativeQuery = true)
    long countPipelineLoss(@Param("facilityId") String facilityId,
                            @Param("startDate") OffsetDateTime startDate,
                            @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT COUNT(*) FROM inbound_event ie " +
            "WHERE ie.status = 'ACCEPTED' " +
            "AND (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate)",
            nativeQuery = true)
    long countAccepted(@Param("facilityId") String facilityId,
                        @Param("startDate") OffsetDateTime startDate,
                        @Param("endDate") OffsetDateTime endDate);

    // --- Source Comparison (moved from EventLogRepository) ---
    // Compares events received from two sources using inbound_event (captures ALL events, not just matched)

    /** Events in sourceA that have a matching event in sourceB (same subject + type within time window) */
    @Query(value = "SELECT COALESCE(a.raw_payload->'data'->>'resourceType', a.raw_payload->>'resourceType', a.type) AS resource_type, " +
            "COUNT(DISTINCT a.id) AS event_count " +
            "FROM inbound_event a " +
            "JOIN inbound_event b ON a.subject = b.subject " +
            "  AND a.type = b.type " +
            "  AND a.event_time IS NOT NULL AND b.event_time IS NOT NULL " +
            "  AND ABS(EXTRACT(EPOCH FROM (a.event_time - b.event_time))) <= :windowSeconds " +
            "WHERE a.source = :sourceA AND b.source = :sourceB " +
            "AND a.status != 'DUPLICATE' AND b.status != 'DUPLICATE' " +
            "AND (CAST(:facilityId AS text) IS NULL OR a.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR a.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR a.received_at <= :endDate) " +
            "GROUP BY resource_type ORDER BY event_count DESC",
            nativeQuery = true)
    List<Object[]> findOverlappingEvents(@Param("sourceA") String sourceA,
                                          @Param("sourceB") String sourceB,
                                          @Param("windowSeconds") long windowSeconds,
                                          @Param("facilityId") String facilityId,
                                          @Param("startDate") OffsetDateTime startDate,
                                          @Param("endDate") OffsetDateTime endDate);

    /** Events in the given source that do NOT have a match in the other source */
    @Query(value = "SELECT COALESCE(a.raw_payload->'data'->>'resourceType', a.raw_payload->>'resourceType', a.type) AS resource_type, " +
            "COUNT(*) AS event_count " +
            "FROM inbound_event a " +
            "WHERE a.source = :source " +
            "AND a.status != 'DUPLICATE' " +
            "AND (CAST(:facilityId AS text) IS NULL OR a.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR a.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR a.received_at <= :endDate) " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM inbound_event b " +
            "  WHERE b.source = :otherSource " +
            "  AND b.status != 'DUPLICATE' " +
            "  AND b.subject = a.subject " +
            "  AND b.type = a.type " +
            "  AND a.event_time IS NOT NULL AND b.event_time IS NOT NULL " +
            "  AND ABS(EXTRACT(EPOCH FROM (a.event_time - b.event_time))) <= :windowSeconds" +
            ") " +
            "GROUP BY resource_type ORDER BY event_count DESC",
            nativeQuery = true)
    List<Object[]> findUniqueToSource(@Param("source") String source,
                                       @Param("otherSource") String otherSource,
                                       @Param("windowSeconds") long windowSeconds,
                                       @Param("facilityId") String facilityId,
                                       @Param("startDate") OffsetDateTime startDate,
                                       @Param("endDate") OffsetDateTime endDate);

    /** Sample overlapping event pairs for drill-down */
    @Query(value = "SELECT a.id AS event_a_id, b.id AS event_b_id, " +
            "a.subject, COALESCE(a.raw_payload->'data'->>'resourceType', a.raw_payload->>'resourceType', a.type) AS resource_type, " +
            "a.event_time AS time_a, b.event_time AS time_b, " +
            "EXTRACT(EPOCH FROM (a.event_time - b.event_time)) AS diff_seconds " +
            "FROM inbound_event a " +
            "JOIN inbound_event b ON a.subject = b.subject " +
            "  AND a.type = b.type " +
            "  AND a.event_time IS NOT NULL AND b.event_time IS NOT NULL " +
            "  AND ABS(EXTRACT(EPOCH FROM (a.event_time - b.event_time))) <= :windowSeconds " +
            "WHERE a.source = :sourceA AND b.source = :sourceB " +
            "AND a.status != 'DUPLICATE' AND b.status != 'DUPLICATE' " +
            "AND (CAST(:facilityId AS text) IS NULL OR a.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR a.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR a.received_at <= :endDate) " +
            "ORDER BY a.received_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findOverlappingEventSamples(@Param("sourceA") String sourceA,
                                                @Param("sourceB") String sourceB,
                                                @Param("windowSeconds") long windowSeconds,
                                                @Param("facilityId") String facilityId,
                                                @Param("startDate") OffsetDateTime startDate,
                                                @Param("endDate") OffsetDateTime endDate,
                                                @Param("limit") int limit);

    // --- Event Volume Trends (by period + resource type) ---

    @Query(value = "SELECT DATE_TRUNC(:interval, ie.event_time) AS period, " +
            "COALESCE(ie.raw_payload->'data'->>'resourceType', ie.raw_payload->>'resourceType', ie.type) AS resource_type, " +
            "COUNT(*) AS event_count " +
            "FROM inbound_event ie " +
            "WHERE ie.status != 'DUPLICATE' " +
            "AND (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:source AS text) IS NULL OR ie.source = :source) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.event_time >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.event_time <= :endDate) " +
            "GROUP BY period, resource_type ORDER BY period",
            nativeQuery = true)
    List<Object[]> findEventTrends(@Param("interval") String interval,
                                    @Param("facilityId") String facilityId,
                                    @Param("source") String source,
                                    @Param("startDate") OffsetDateTime startDate,
                                    @Param("endDate") OffsetDateTime endDate);

    // --- Ingestion by source (events received, with status breakdown) ---

    @Query(value = "SELECT ie.source, COUNT(*) AS total_events " +
            "FROM inbound_event ie " +
            "WHERE (CAST(:facilityId AS text) IS NULL OR ie.facility_id = :facilityId) " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR ie.received_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR ie.received_at <= :endDate) " +
            "GROUP BY ie.source ORDER BY total_events DESC",
            nativeQuery = true)
    List<Object[]> countBySource(@Param("facilityId") String facilityId,
                                  @Param("startDate") OffsetDateTime startDate,
                                  @Param("endDate") OffsetDateTime endDate);
}
