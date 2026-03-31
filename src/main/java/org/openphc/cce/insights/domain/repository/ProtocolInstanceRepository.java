package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ProtocolInstanceRepository extends ReadOnlyRepository<ProtocolInstance, UUID> {

    List<ProtocolInstance> findByPatientId(String patientId);

    List<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId);

    Page<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId, Pageable pageable);

    @Query("SELECT pi.status, COUNT(pi) FROM ProtocolInstance pi " +
            "WHERE pi.protocolDefinitionId = :protocolDefId " +
            "GROUP BY pi.status")
    List<Object[]> countByProtocolDefinitionIdGroupByStatus(@Param("protocolDefId") UUID protocolDefId);

    @Query(value = "SELECT DATE_TRUNC(:interval, pi.enrolled_at) AS period, COUNT(*) AS enrollments " +
            "FROM protocol_instance pi " +
            "WHERE pi.protocol_definition_id = :protocolDefId " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR pi.enrolled_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR pi.enrolled_at <= :endDate) " +
            "GROUP BY period ORDER BY period",
            nativeQuery = true)
    List<Object[]> findEnrollmentTrends(@Param("protocolDefId") UUID protocolDefId,
                                        @Param("interval") String interval,
                                        @Param("startDate") OffsetDateTime startDate,
                                        @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT pi FROM ProtocolInstance pi " +
            "WHERE pi.protocolDefinitionId = :protocolDefId " +
            "AND pi.status = :status")
    Page<ProtocolInstance> findByProtocolDefinitionIdAndStatus(
            @Param("protocolDefId") UUID protocolDefId,
            @Param("status") ProtocolInstanceStatus status,
            Pageable pageable);
}
