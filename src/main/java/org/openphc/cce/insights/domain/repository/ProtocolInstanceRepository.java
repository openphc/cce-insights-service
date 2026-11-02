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

    /** Distinct patients with an enrolment whose clinical enrolled_at falls within [startDate, endDate]. */
    long countDistinctPatientsEnrolledWithin(OffsetDateTime startDate, OffsetDateTime endDate);

    List<ProtocolInstance> findByPatientId(String patientId);

    List<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId);

    /** Enrolments of a protocol with enrolled_at in [startDate, endDate] (null bounds are open), newest first. */
    Page<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId,
                                                      OffsetDateTime startDate, OffsetDateTime endDate,
                                                      Pageable pageable);

    List<Object[]> countByProtocolDefinitionIdGroupByStatus(UUID protocolDefId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findEnrollmentTrends(UUID protocolDefId, String interval,
                                        OffsetDateTime startDate, OffsetDateTime endDate);

    Page<ProtocolInstance> findByProtocolDefinitionIdAndStatus(UUID protocolDefId,
                                                               ProtocolInstanceStatus status,
                                                               Pageable pageable);

    Page<ProtocolInstance> findByProtocolDefinitionIdAndPatientIdContaining(UUID protocolDefId,
                                                                             String patientId,
                                                                             OffsetDateTime startDate,
                                                                             OffsetDateTime endDate,
                                                                             Pageable pageable);
}
