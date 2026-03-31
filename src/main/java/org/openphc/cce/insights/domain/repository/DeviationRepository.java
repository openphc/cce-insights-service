package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.enums.DeviationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviationRepository extends ReadOnlyRepository<Deviation, UUID> {

    List<Deviation> findByProtocolInstanceId(UUID protocolInstanceId);

    Page<Deviation> findByDeviationType(DeviationType type, Pageable pageable);

    @Query(value = "SELECT DATE_TRUNC(:interval, d.detected_at) AS period, " +
            "d.deviation_type, COUNT(*) AS count " +
            "FROM deviation d " +
            "WHERE (:startDate IS NULL OR d.detected_at >= :startDate) " +
            "AND (:endDate IS NULL OR d.detected_at <= :endDate) " +
            "AND (:facilityId IS NULL OR EXISTS (" +
            "  SELECT 1 FROM event_log el WHERE el.protocol_instance_id = d.protocol_instance_id " +
            "  AND el.facility_id = :facilityId)) " +
            "GROUP BY period, d.deviation_type ORDER BY period",
            nativeQuery = true)
    List<Object[]> findDeviationTrends(@Param("interval") String interval,
                                       @Param("startDate") OffsetDateTime startDate,
                                       @Param("endDate") OffsetDateTime endDate,
                                       @Param("facilityId") String facilityId);

    @Query(value = "SELECT si.action_id, pi.protocol_definition_id, pi.protocol_canonical, " +
            "COUNT(*) AS total_deviations, " +
            "COUNT(CASE WHEN d.deviation_type = 'OVERDUE' THEN 1 END) AS overdue_count, " +
            "COUNT(CASE WHEN d.deviation_type = 'MISSED' THEN 1 END) AS missed_count, " +
            "COUNT(DISTINCT pi.patient_id) AS affected_patients " +
            "FROM deviation d " +
            "JOIN step_instance si ON d.step_instance_id = si.id " +
            "JOIN protocol_instance pi ON d.protocol_instance_id = pi.id " +
            "WHERE (:protocolDefId IS NULL OR pi.protocol_definition_id = :protocolDefId) " +
            "AND (:startDate IS NULL OR d.detected_at >= :startDate) " +
            "AND (:endDate IS NULL OR d.detected_at <= :endDate) " +
            "GROUP BY si.action_id, pi.protocol_definition_id, pi.protocol_canonical " +
            "ORDER BY total_deviations DESC",
            nativeQuery = true)
    List<Object[]> findDeviationsByAction(@Param("protocolDefId") UUID protocolDefId,
                                          @Param("startDate") OffsetDateTime startDate,
                                          @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT " +
            "COUNT(*) FILTER (WHERE si.state = 'COMPLETED') AS resolved_count, " +
            "COUNT(*) FILTER (WHERE si.state = 'MISSED') AS escalated_count, " +
            "COUNT(*) AS total_overdue, " +
            "AVG(EXTRACT(EPOCH FROM (si.completed_at - d.detected_at)) / 86400.0) " +
            "  FILTER (WHERE si.state = 'COMPLETED') AS avg_days_to_resolve " +
            "FROM deviation d " +
            "JOIN step_instance si ON d.step_instance_id = si.id " +
            "WHERE d.deviation_type = 'OVERDUE' " +
            "AND (:protocolDefId IS NULL OR EXISTS (" +
            "  SELECT 1 FROM protocol_instance pi WHERE pi.id = d.protocol_instance_id " +
            "  AND pi.protocol_definition_id = :protocolDefId)) " +
            "AND (:startDate IS NULL OR d.detected_at >= :startDate) " +
            "AND (:endDate IS NULL OR d.detected_at <= :endDate)",
            nativeQuery = true)
    List<Object[]> findResolutionRate(@Param("protocolDefId") UUID protocolDefId,
                                      @Param("startDate") OffsetDateTime startDate,
                                      @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT d.deviationType, COUNT(d) FROM Deviation d " +
            "WHERE d.detectedAt >= :since " +
            "GROUP BY d.deviationType")
    List<Object[]> countByTypeSince(@Param("since") OffsetDateTime since);

    @Query(value = "SELECT pi.patient_id, COUNT(*) AS total_deviations, " +
            "COUNT(CASE WHEN d.deviation_type = 'OVERDUE' THEN 1 END) AS overdue_count, " +
            "COUNT(CASE WHEN d.deviation_type = 'MISSED' THEN 1 END) AS missed_count, " +
            "COUNT(DISTINCT pi.id) AS affected_protocols, " +
            "COUNT(DISTINCT d.step_instance_id) AS affected_steps " +
            "FROM deviation d " +
            "JOIN protocol_instance pi ON d.protocol_instance_id = pi.id " +
            "WHERE (:facilityId IS NULL OR EXISTS (" +
            "  SELECT 1 FROM event_log el WHERE el.protocol_instance_id = pi.id " +
            "  AND el.facility_id = :facilityId)) " +
            "AND (:startDate IS NULL OR d.detected_at >= :startDate) " +
            "AND (:endDate IS NULL OR d.detected_at <= :endDate) " +
            "GROUP BY pi.patient_id " +
            "HAVING COUNT(*) >= :minDeviations " +
            "ORDER BY total_deviations DESC",
            nativeQuery = true)
    List<Object[]> findRepeatDeviationPatients(@Param("minDeviations") int minDeviations,
                                               @Param("facilityId") String facilityId,
                                               @Param("startDate") OffsetDateTime startDate,
                                               @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT el.facility_id, COUNT(*) AS deviation_count " +
            "FROM deviation d " +
            "JOIN protocol_instance pi ON d.protocol_instance_id = pi.id " +
            "JOIN event_log el ON el.protocol_instance_id = pi.id " +
            "WHERE el.facility_id IS NOT NULL " +
            "GROUP BY el.facility_id",
            nativeQuery = true)
    List<Object[]> countDeviationsByFacility();
}
