package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.IntelligenceDelivery;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface IntelligenceDeliveryRepository extends ReadOnlyRepository<IntelligenceDelivery, UUID> {

    List<Object[]> countByStatus();

    List<Object[]> countByActionType();

    List<Object[]> countBySeverity();

    List<Object[]> countByDestination();

    long countDelivered();

    long countFailed();

    Double avgDeliveryLatencySeconds();

    long countFiltered(OffsetDateTime startDate, OffsetDateTime endDate);

    long countDeliveredFiltered(OffsetDateTime startDate, OffsetDateTime endDate);

    long countFailedFiltered(OffsetDateTime startDate, OffsetDateTime endDate);

    Double avgDeliveryLatencySecondsFiltered(OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByStatusFiltered(OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByActionTypeFiltered(OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countBySeverityFiltered(OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByDestinationFiltered(OffsetDateTime startDate, OffsetDateTime endDate);
}
