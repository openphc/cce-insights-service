package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.StepInstance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface StepInstanceRepository extends ReadOnlyRepository<StepInstance, UUID> {

    List<StepInstance> findByProtocolInstanceId(UUID protocolInstanceId);

    List<StepInstance> findByProtocolInstanceIdOrderByDueDateAsc(UUID protocolInstanceId);

    /** Returns rows of [step_status, sla_status ('' = not judged), count]. */
    List<Object[]> countByProtocolInstanceIdGroupByStatus(UUID protocolInstanceId);

    /**
     * SLA thresholds from step_sla_state_transitions (mandatory steps only — others have no row).
     * Returns rows of [stepInstanceId(UUID), dueThreshold(OffsetDateTime, DUE_DATE_REACHED.process_by),
     *                  missedThreshold(OffsetDateTime, MISSED_DATE_REACHED.process_by)]; either may be null.
     */
    List<Object[]> findSlaThresholdsByStepInstanceIdIn(List<UUID> stepInstanceIds);

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

    /**
     * Per-facility patient risk counts (on-track / at-risk / non-compliant), aggregated entirely
     * in ClickHouse. Replaces the previous approach of loading every protocol_instance and step_instance
     * into Java and joining them there, which broke on dev once the IN (...) clause grew past a few
     * hundred ids (ClickHouse HTTP transport rejected the request — Code: 62, transport error: 400).
     * Returns one row per facility: [facilityId, nonCompliantCount, atRiskCount, onTrackCount].
     */
    List<Object[]> findAtRiskHotspotCounts();

    // Returns one row per protocol: [protocolDefinitionId, protocolCanonical, enrollments, totalSteps, completedSteps]
    List<Object[]> findProtocolStepMetricsByFacility(String facilityId);

    // Returns single row, same order as the step_* columns of mv_daily_compliance_kpis:
    // [completed, notStarted, slaMet, slaOverdue, slaMissed, slaUnjudged, completedOnTime, completedLate,
    //  totalSteps, totalEnrollments]
    Object[] aggregateStepMetrics(UUID protocolDefinitionId);

    Object[] aggregateStepMetricsAll();

    Object[] aggregateStepMetricsByFacility(String facilityId);

    Object[] aggregateStepMetricsByProtocolAndFacility(UUID protocolDefinitionId, String facilityId);

    // Returns single row: [totalReceived, totalVerified] — completed counts for the
    // consent-request and consent-verification steps, across all protocols.
    Object[] aggregateConsentMetrics();
}
