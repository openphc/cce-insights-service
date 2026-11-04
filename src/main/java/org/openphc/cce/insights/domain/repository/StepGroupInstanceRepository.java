package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.StepGroupInstance;

import java.util.List;
import java.util.UUID;

public interface StepGroupInstanceRepository extends ReadOnlyRepository<StepGroupInstance, UUID> {

    /** Every group occurrence of the given enrollments, in one query per 1000 ids. */
    List<StepGroupInstance> findByProtocolInstanceIdIn(List<UUID> protocolInstanceIds);
}
