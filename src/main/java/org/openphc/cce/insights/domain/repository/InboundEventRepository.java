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

    // received_at (system-time) variant — Ingestion pipeline-loss denominator only.
    long countAcceptedByReceivedAt(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    // Facility ranking "Events (period)" — grouped read of the event_time event-volume MV. [facility_id, count]
    List<Object[]> eventCountByFacilityFromMv(OffsetDateTime startDate, OffsetDateTime endDate);

    // Events page — clinical volume from mv_event_volume_hourly (event_time) + processing from mv_daily_event_kpis.
    List<Object[]> eventVolumeByResourceType(String facilityId, String source, OffsetDateTime startDate, OffsetDateTime endDate);
    List<Object[]> eventVolumeByFacilityAndType(String facilityId, String source, String resourceType, OffsetDateTime startDate, OffsetDateTime endDate);
    List<Object[]> eventVolumeBySource(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);
    List<Object[]> eventVolumeTrends(String interval, String facilityId, String source, OffsetDateTime startDate, OffsetDateTime endDate);
    Object[] eventProcessingKpis(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    /** Returns true if this facility transmitted ≥1 successful HIE submission in the range. */
    boolean facilityTransmittedInRange(String facilityId,
                                       OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                    OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countBySource(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Total count of referral forms successfully received by HIE, event_time-keyed.
     * A "referral form successfully received" is an {@code inbound_event_logs} row with
     * {@code status='ACCEPTED'} whose matched step's {@code action_id} ends with
     * {@code -referral} (Referral Initiated step; not {@code -referral-ack} or
     * {@code -referral-consultation}).
     *
     * @param facilityId optional single-facility scope (event payload facility_id)
     */
    long countReferralsReceivedByHIE(String facilityId,
                                     OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Same definition as {@link #countReferralsReceivedByHIE}, grouped by
     * {@code inbound_event_logs.facility_id}. Rows: {@code [facility_id, count]}.
     */
    List<Object[]> countReferralsReceivedByHIEGroupedByFacility(OffsetDateTime startDate,
                                                                OffsetDateTime endDate);
}
