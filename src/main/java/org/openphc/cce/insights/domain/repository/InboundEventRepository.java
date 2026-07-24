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

    /**
     * RI-36 tracked cohort — distinct patients whose events are "considered by a protocol" in the
     * range: ACCEPTED inbound events with {@code event_time} in [start,end] whose {@code cloudevents_id}
     * matched a protocol (compliance_event_logs {@code processing_status='MATCHED'}), whether they
     * created a new enrollment or advanced an existing journey. Scoped by {@code event_time} (not
     * enrolled_at) and facility ({@code null}/'' = all). Optional dates (null = unbounded).
     */
    long countDistinctPatientsWithMatchedEvents(String facilityId, String district,
                                                OffsetDateTime startDate, OffsetDateTime endDate);

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
    List<Object[]> eventVolumeByResourceType(String facilityId, String source, String district, OffsetDateTime startDate, OffsetDateTime endDate);
    List<Object[]> eventVolumeByFacilityAndType(String facilityId, String source, String resourceType, String district, OffsetDateTime startDate, OffsetDateTime endDate);
    List<Object[]> eventVolumeBySource(String facilityId, String district, OffsetDateTime startDate, OffsetDateTime endDate);
    List<Object[]> eventVolumeTrends(String interval, String facilityId, String source, String district, OffsetDateTime startDate, OffsetDateTime endDate);
    Object[] eventProcessingKpis(String facilityId, String district, OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Zero-match event breakdown (Events page drill-down table): ACCEPTED inbound events whose
     * compliance_event_logs.processing_status = 'ZERO_MATCH', grouped by resource type, clinical
     * code, category and facility. Code/category are extracted ad-hoc from raw_payload (not
     * materialized columns) since their JSON path varies by FHIR resourceType.
     * Rows: {@code [resourceType(String), code(String), category(String), facilityId(String), count(Long)]}.
     */
    List<Object[]> findZeroMatchEvents(String facilityId, String district,
                                        OffsetDateTime startDate, OffsetDateTime endDate);

    /** Most recent inbound_event_logs.received_at across all events — pipeline freshness indicator. */
    OffsetDateTime findLastReceivedAt();

    /** Returns true if this facility transmitted ≥1 successful HIE submission in the range. */
    boolean facilityTransmittedInRange(String facilityId,
                                       OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> findEventTrends(String interval, String facilityId, String source,
                                    OffsetDateTime startDate, OffsetDateTime endDate);

    List<Object[]> countBySource(String facilityId, OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Total count of referrals RECEIVED by HIE, event_time-keyed (mv_daily_referral_kpis.referral_count).
     * A "referral received" is an ACCEPTED inbound event that is either a prod TRANSFER_ENCOUNTER
     * Encounter or a dev/demo event that completed a Referral step (see schema/07 section 7).
     *
     * @param facilityId optional single-facility scope (event payload facility_id)
     */
    long countReferralsReceivedByHIE(String facilityId,
                                     OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Total count of "compliant" referrals (mv_daily_referral_kpis.matched_count): received referrals
     * that were matched to (completed) a Referral step in a tracked care journey. Non-compliant =
     * received - matched. Same scope/keys as {@link #countReferralsReceivedByHIE}.
     */
    long countReferralsMatched(String facilityId,
                               OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Referral counts grouped by {@code inbound_event_logs.facility_id}.
     * Rows: {@code [facility_id(String), received(Long), matched(Long)]}.
     */
    List<Object[]> countReferralsReceivedByHIEGroupedByFacility(OffsetDateTime startDate,
                                                                OffsetDateTime endDate);

    /**
     * Referrals received by HIE, grouped by PATIENT (RI-44 patient drill-down). Same referral-event
     * definition as {@link #countReferralsReceivedByHIE} / mv_daily_referral_kpis (prod
     * TRANSFER_ENCOUNTER Encounter ∪ dev/demo referral-step), scoped by clinical event_time.
     * One row per distinct patient (inbound_event_logs.subject).
     * Rows: {@code [patientId(String), facilityId(String), lastReferral(String), referralCount(Long), matchedCount(Long)]}.
     */
    List<Object[]> referralsReceivedByHIEByPatient(OffsetDateTime startDate, OffsetDateTime endDate);
}
