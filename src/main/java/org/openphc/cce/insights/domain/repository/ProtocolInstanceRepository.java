package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ProtocolInstanceRepository extends ReadOnlyRepository<ProtocolInstance, UUID> {

    List<String> findDistinctPatientIds();

    List<ProtocolInstance> findByPatientId(String patientId);

    List<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId);

    Page<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId, Pageable pageable);

    List<Object[]> countByProtocolDefinitionIdGroupByStatus(UUID protocolDefId);

    List<Object[]> findEnrollmentTrends(UUID protocolDefId, String interval,
                                        OffsetDateTime startDate, OffsetDateTime endDate);

    Page<ProtocolInstance> findByProtocolDefinitionIdAndStatus(UUID protocolDefId,
                                                               ProtocolInstanceStatus status,
                                                               Pageable pageable);

    Page<ProtocolInstance> findByProtocolDefinitionIdAndPatientIdContaining(UUID protocolDefId,
                                                                             String patientId,
                                                                             Pageable pageable);
}
