package org.openphc.cce.insights.domain.repository;

import org.openphc.cce.insights.domain.entity.InboundEvent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface InboundEventRepository extends ReadOnlyRepository<InboundEvent, UUID> {

    List<String> findDistinctSources();

    long countDistinctPatientSubjectsBySource(String source, String facilityId,
                                              OffsetDateTime startDate, OffsetDateTime endDate);

    long countDistinctActiveFacilities(OffsetDateTime startDate, OffsetDateTime endDate);

    long countEventsBySource(String source, String facilityId,
                             OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countDistinctPatientsBySourceGroupedByFacility(String source,
                                                                   OffsetDateTime startDate,
                                                                   OffsetDateTime endDate);

    List<Object[]> countByStatus(String facilityId, String source,
                                  OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countByRejectionReason(String facilityId, String source,
                                           OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findIngestionTrends(String interval, String facilityId, String source,
                                        OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countBySourceAndStatus(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countBySourceAndRejectionReason(String facilityId,
                                                    OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findPipelineLossBySource(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    long countPipelineLoss(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    long countAccepted(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                    OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countBySource(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);
}
