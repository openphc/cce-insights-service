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

    /** Protocol instances that had step activity (step updated_at) in the given window. */
    List<ProtocolInstance> findByProtocolDefinitionIdWithActivityBetween(UUID protocolDefinitionId,
                                                                          OffsetDateTime startDate,
                                                                          OffsetDateTime endDate);

    /** All protocol_instance rows whose enrolled_at falls in the optional bounds. */
    List<ProtocolInstance> findEnrolledBetween(OffsetDateTime startDate, OffsetDateTime endDate);

    Page<ProtocolInstance> findByProtocolDefinitionId(UUID protocolDefinitionId, Pageable pageable);

    List<Object[]> countByProtocolDefinitionIdGroupByStatus(UUID protocolDefId);

    List<Object[]> findEnrollmentTrends(UUID protocolDefId, String interval,
                                        OffsetDateTime startDate, OffsetDateTime endDate);

    /** Same as {@link #findEnrollmentTrends} narrowed to a specific facility (via mv_patient_facility_latest). */
    List<Object[]> findEnrollmentTrendsByFacility(UUID protocolDefId, String facilityId, String interval,
                                                   OffsetDateTime startDate, OffsetDateTime endDate);

    Page<ProtocolInstance> findByProtocolDefinitionIdAndStatus(UUID protocolDefId,
                                                               ProtocolInstanceStatus status,
                                                               Pageable pageable);

    Page<ProtocolInstance> findByProtocolDefinitionIdAndPatientIdContaining(UUID protocolDefId,
                                                                             String patientId,
                                                                             Pageable pageable);

    /**
     * Distinct enrolled patients per facility via mv_patient_facility_latest.
     * Returns rows of [facility_id(String), tracked_patients(long), non_compliant_patients(long),
     * deviations(long)]. When dates are provided, tracked = enrolled in range; non-compliant =
     * enrolled-in-range patients with a deviation detected in range; deviations = deviations detected
     * in range for that same cohort. All three share one cohort so a leaderboard row is consistent.
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

    /**
     * Distinct patient IDs whose CURRENT facility (mv_patient_facility_latest) is the given facility.
     * This is the canonical "patients at a facility" definition used across the compliance page so
     * every card scopes to the same patient set. Note: mv_patient_facility_latest holds only the
     * latest facility per patient (no history), so this is current-facility membership.
     */
    List<String> findPatientIdsAtFacility(String facilityId);
}
