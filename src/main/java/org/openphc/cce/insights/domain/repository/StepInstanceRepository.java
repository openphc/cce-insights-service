package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.StepInstance;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StepInstanceRepository extends ReadOnlyRepository<StepInstance, UUID> {

    List<StepInstance> findByProtocolInstanceId(UUID protocolInstanceId);

    List<StepInstance> findByProtocolInstanceIdOrderByDueDateAsc(UUID protocolInstanceId);

    @Query("SELECT si.state, COUNT(si) FROM StepInstance si " +
            "WHERE si.protocolInstanceId = :piId " +
            "GROUP BY si.state")
    List<Object[]> countByProtocolInstanceIdGroupByState(@Param("piId") UUID protocolInstanceId);

    @Query(value = "SELECT si.action_id, " +
            "COUNT(*) AS total_instances, " +
            "COUNT(CASE WHEN si.state = 'COMPLETED' THEN 1 END) AS completed_count, " +
            "COUNT(CASE WHEN si.completion_status = 'EARLY' THEN 1 END) AS early_count, " +
            "COUNT(CASE WHEN si.completion_status = 'ON_TIME' THEN 1 END) AS on_time_count, " +
            "COUNT(CASE WHEN si.completion_status = 'LATE' THEN 1 END) AS late_count, " +
            "COUNT(CASE WHEN si.state = 'OVERDUE' THEN 1 END) AS overdue_count, " +
            "COUNT(CASE WHEN si.state = 'MISSED' THEN 1 END) AS missed_count, " +
            "COUNT(CASE WHEN si.state = 'SKIPPED' THEN 1 END) AS skipped_count, " +
            "COUNT(CASE WHEN si.state = 'PENDING' OR si.state = 'DUE' THEN 1 END) AS pending_count, " +
            "AVG(EXTRACT(EPOCH FROM (si.completed_at - si.due_date)) / 86400.0) " +
            "  FILTER (WHERE si.state = 'COMPLETED' AND si.due_date IS NOT NULL) AS avg_days_to_complete, " +
            "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (si.completed_at - si.due_date)) / 86400.0) " +
            "  FILTER (WHERE si.state = 'COMPLETED' AND si.due_date IS NOT NULL) AS median_days_to_complete " +
            "FROM step_instance si " +
            "JOIN protocol_instance pi ON si.protocol_instance_id = pi.id " +
            "WHERE pi.protocol_definition_id = :protocolDefId " +
            "GROUP BY si.action_id",
            nativeQuery = true)
    List<Object[]> findStepAnalytics(@Param("protocolDefId") UUID protocolDefId);

    @Query(value = "SELECT si.action_id, " +
            "COUNT(DISTINCT pi.patient_id) AS reached_count, " +
            "COUNT(DISTINCT CASE WHEN si.state = 'COMPLETED' THEN pi.patient_id END) AS completed_count " +
            "FROM step_instance si " +
            "JOIN protocol_instance pi ON si.protocol_instance_id = pi.id " +
            "WHERE pi.protocol_definition_id = :protocolDefId " +
            "GROUP BY si.action_id",
            nativeQuery = true)
    List<Object[]> findCompletionFunnel(@Param("protocolDefId") UUID protocolDefId);

    @Query(value = "SELECT el.facility_id, " +
            "COUNT(*) AS total_steps, " +
            "COUNT(CASE WHEN si.state IN ('COMPLETED','SKIPPED') THEN 1 END) AS completed_steps " +
            "FROM step_instance si " +
            "JOIN protocol_instance pi ON si.protocol_instance_id = pi.id " +
            "JOIN event_log el ON el.protocol_instance_id = pi.id " +
            "WHERE el.facility_id IS NOT NULL " +
            "GROUP BY el.facility_id",
            nativeQuery = true)
    List<Object[]> findStepComplianceByFacility();
}
