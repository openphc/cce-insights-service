package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.IntelligenceDelivery;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface IntelligenceDeliveryRepository extends ReadOnlyRepository<IntelligenceDelivery, UUID> {

    @Query(value = "SELECT status, COUNT(*) FROM intelligence_delivery GROUP BY status", nativeQuery = true)
    List<Object[]> countByStatus();

    @Query(value = "SELECT action_type, COUNT(*) FROM intelligence_delivery GROUP BY action_type", nativeQuery = true)
    List<Object[]> countByActionType();

    @Query(value = "SELECT severity, COUNT(*) FROM intelligence_delivery GROUP BY severity", nativeQuery = true)
    List<Object[]> countBySeverity();

    @Query(value = "SELECT destination, COUNT(*) AS cnt FROM intelligence_delivery GROUP BY destination ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> countByDestination();

    @Query(value = "SELECT COUNT(*) FROM intelligence_delivery WHERE status = 'DELIVERED'", nativeQuery = true)
    long countDelivered();

    @Query(value = "SELECT COUNT(*) FROM intelligence_delivery WHERE status = 'FAILED'", nativeQuery = true)
    long countFailed();

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (delivered_at - created_at))) FROM intelligence_delivery WHERE status = 'DELIVERED' AND delivered_at IS NOT NULL", nativeQuery = true)
    Double avgDeliveryLatencySeconds();
}
