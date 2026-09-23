package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.StepInstance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface StepInstanceRepository extends ReadOnlyRepository<StepInstance, UUID> {

    List<StepInstance> findByProtocolInstanceId(UUID protocolInstanceId);

    List<StepInstance> findByProtocolInstanceIdOrderByDueDateAsc(UUID protocolInstanceId);

    List<Object[]> countByProtocolInstanceIdGroupByState(UUID protocolInstanceId);

    List<Object[]> findStepAnalytics(UUID protocolDefId);

    List<Object[]> findStepAnalyticsByFacility(UUID protocolDefId, String facilityId);

    List<Object[]> findCompletionFunnel(UUID protocolDefId);

    List<Object[]> findStepComplianceByFacility(UUID protocolDefinitionId);

    List<Object[]> findReferralEventCountsByFacility(UUID protocolDefinitionId);

    List<Object[]> findStepComplianceByPractitioner();

    List<Object[]> findStepComplianceByPractitionerFiltered(OffsetDateTime startDate,
                                                            OffsetDateTime endDate,
                                                            String facilityId);

    /**
     * Step compliance (total/completed) grouped by patient_id, for a specific set of patients.
     * Used by PractitionerRankingService to roll patient-level compliance up to a practitioner
     * once the practitioner-to-patient mapping is resolved in Java (see PractitionerExtractor)
     * rather than via the unpopulated inbound_event_logs.practitioner_ref column.
     */
    List<Object[]> findStepComplianceByPatientIds(List<String> patientIds);

    // Batch load — replaces per-instance findByProtocolInstanceId calls in paged loops
    List<StepInstance> findByProtocolInstanceIdIn(List<UUID> ids);

    // Returns one row per protocol: [protocolDefinitionId, protocolCanonical, enrollments, totalSteps, completedSteps]
    List<Object[]> findProtocolStepMetricsByFacility(String facilityId);

    // Returns single row: [completed, overdue, missed, due, pending, early, onTime, late, totalSteps, totalEnrollments]
    Object[] aggregateStepMetrics(UUID protocolDefinitionId);

    Object[] aggregateStepMetricsAll();

    Object[] aggregateStepMetricsByFacility(String facilityId);

    Object[] aggregateStepMetricsByProtocolAndFacility(UUID protocolDefinitionId, String facilityId);
}
