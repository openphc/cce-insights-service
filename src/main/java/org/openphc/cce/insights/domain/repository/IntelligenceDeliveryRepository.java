package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.IntelligenceDelivery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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

    // Date-filtered versions
    @Query(value = "SELECT COUNT(*) FROM intelligence_delivery " +
            "WHERE (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate)",
            nativeQuery = true)
    long countFiltered(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT COUNT(*) FROM intelligence_delivery WHERE status = 'DELIVERED' " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate)",
            nativeQuery = true)
    long countDeliveredFiltered(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT COUNT(*) FROM intelligence_delivery WHERE status = 'FAILED' " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate)",
            nativeQuery = true)
    long countFailedFiltered(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (delivered_at - created_at))) FROM intelligence_delivery " +
            "WHERE status = 'DELIVERED' AND delivered_at IS NOT NULL " +
            "AND (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate)",
            nativeQuery = true)
    Double avgDeliveryLatencySecondsFiltered(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT status, COUNT(*) FROM intelligence_delivery " +
            "WHERE (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate) " +
            "GROUP BY status", nativeQuery = true)
    List<Object[]> countByStatusFiltered(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT action_type, COUNT(*) FROM intelligence_delivery " +
            "WHERE (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate) " +
            "GROUP BY action_type", nativeQuery = true)
    List<Object[]> countByActionTypeFiltered(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT severity, COUNT(*) FROM intelligence_delivery " +
            "WHERE (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate) " +
            "GROUP BY severity", nativeQuery = true)
    List<Object[]> countBySeverityFiltered(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query(value = "SELECT destination, COUNT(*) AS cnt FROM intelligence_delivery " +
            "WHERE (CAST(:startDate AS timestamptz) IS NULL OR created_at >= :startDate) " +
            "AND (CAST(:endDate AS timestamptz) IS NULL OR created_at <= :endDate) " +
            "GROUP BY destination ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> countByDestinationFiltered(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);
}
