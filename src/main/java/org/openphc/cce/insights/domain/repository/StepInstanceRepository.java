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

    List<Object[]> findStepComplianceByFacility();

    List<Object[]> findReferralEventCountsByFacility();

    List<Object[]> findStepComplianceByPractitioner();

    List<Object[]> findStepComplianceByPractitionerFiltered(OffsetDateTime startDate,
                                                            OffsetDateTime endDate,
                                                            String facilityId);
}
