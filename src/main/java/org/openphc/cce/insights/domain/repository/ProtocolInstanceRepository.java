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

    /** Distinct patients with an enrollment in [startDate, endDate]. Pass null for open bounds. */
    long countDistinctPatientsEnrolledBetween(OffsetDateTime startDate, OffsetDateTime endDate);

    List<ProtocolInstance> findByPatientId(String patientId);

    List<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId);

    /** All enrollments for a protocol with optional enrollment date bounds. */
    List<ProtocolInstance> findByProtocolDefinitionIdAndEnrolledBetween(UUID protocolDefinitionId,
                                                                        OffsetDateTime startDate,
                                                                        OffsetDateTime endDate);

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

    /**
     * Distinct enrolled patients per facility via mv_patient_facility_latest.
     * Returns rows of [facility_id(String), tracked_patients(long), non_compliant_patients(long)].
     * When dates are provided, tracked = enrolled in range; non-compliant = deviation in range.
     */
    List<Object[]> countPatientComplianceByFacility(OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Same shape as {@link #countPatientComplianceByFacility} but scoped to a single facility.
     * Returns a 2-element array: [trackedPatients, nonCompliantPatients].
     */
    long[] countPatientCohortForFacility(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Distinct enrolled patients for a specific facility within an optional date range.
     */
    long countDistinctPatientsForFacility(String facilityId,
                                          OffsetDateTime startDate, OffsetDateTime endDate);
}
