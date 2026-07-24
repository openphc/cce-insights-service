package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.DashboardComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.DashboardOverviewDto;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.openphc.cce.insights.web.dto.PractitionerRankingDto;
import org.openphc.cce.insights.web.dto.ReferralsKpiDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InboundEventRepository inboundEventRepository;
    private final DeviationRepository deviationRepository;
    private final DailyKpiRepository dailyKpiRepository;
    private final FacilityRankingService facilityRankingService;
    private final PractitionerRankingService practitionerRankingService;
    private final DeviationAnalyticsService deviationAnalyticsService;

    @Cacheable(value = "metrics",
            key = "'dashboard-overview-' + #facilityId + '-' + (#district ?: 'all') + '-' + #startDate + '-' + #endDate")
    public DashboardOverviewDto getOverview(String facilityId,
                                             String district,
                                             OffsetDateTime startDate,
                                             OffsetDateTime endDate) {
        // RI-53 — "Patients Received by HIE" = distinct patients whose events are PROTOCOL-TRACKED
        // (ACCEPTED + MATCHED to a protocol) in the range — NOT a raw source-filtered count. Uses the
        // shared matched-cohort query, which is already event_time-scoped and honours the global
        // district filter (facility reference), consistent with the other Dashboard cards.
        long patientsFromHIE = inboundEventRepository.countDistinctPatientsWithMatchedEvents(
                facilityId, district, startDate, endDate);

        // Patients from E-Buzima EMR direct (source = 'ebuzima-direct') — pending integration
        long totalPatientsEBuzima = inboundEventRepository.countDistinctPatientSubjectsBySource(
                "ebuzima-direct", facilityId, startDate, endDate);

        long activeFacilities = inboundEventRepository.countDistinctActiveFacilities(
                startDate, endDate);

        // Total events from ebuzima source (HIE event count for Data Flow Validation)
        long hieEventCount = inboundEventRepository.countEventsBySource(
                "ebuzima", facilityId, startDate, endDate);

        double transmissionRate = totalPatientsEBuzima > 0
                ? Math.round((double) patientsFromHIE / totalPatientsEBuzima * 1000.0) / 10.0
                : 0.0;

        // Deviation summary
        var intel = deviationAnalyticsService.getIntelligenceSummary(startDate, endDate, facilityId);
        long activeDeviations = intel.getTotalDeviations();
        long newDeviations24h = intel.getRecentActivity() != null
                ? intel.getRecentActivity().getLast24Hours() : 0;

        // Per-facility HIE patient counts
        Map<String, Long> facilityHIEPatients = new LinkedHashMap<>();
        for (Object[] row : inboundEventRepository.countDistinctPatientsBySourceGroupedByFacility(
                "ebuzima", startDate, endDate)) {
            facilityHIEPatients.put((String) row[0], ((Number) row[1]).longValue());
        }

        LocalDate rangeStart = startDate != null ? startDate.toLocalDate() : null;
        LocalDate rangeEnd   = endDate   != null ? endDate.toLocalDate()   : null;

        // Top 3 and Bottom 3 facilities by compliance rate
        List<FacilityRankingDto> topFacilities = facilityRankingService.getRankings(
                null, facilityId, rangeStart, rangeEnd, "complianceRate", "desc", 3);
        List<FacilityRankingDto> bottomFacilities = facilityRankingService.getRankings(
                null, facilityId, rangeStart, rangeEnd, "complianceRate", "asc", 3);

        // Enrich facility rankings with HIE patient counts
        enrichFacilitiesWithHIE(topFacilities, facilityHIEPatients);
        enrichFacilitiesWithHIE(bottomFacilities, facilityHIEPatients);

        return DashboardOverviewDto.builder()
                .totalPatientsEBuzima(totalPatientsEBuzima)
                .patientsReceivedHIE(patientsFromHIE)
                .transmissionRate(transmissionRate)
                .activeFacilities(activeFacilities)
                .activeDeviations(activeDeviations)
                .newDeviations24h(newDeviations24h)
                .hieEventCount(hieEventCount)
                .topFacilities(topFacilities)
                .bottomFacilities(bottomFacilities)
                .build();
    }

    @Cacheable(value = "metrics", key = "'dashboard-compliance-summary-' + (#facilityId ?: 'all') + '-' + #startDate + '-' + #endDate")
    public DashboardComplianceSummaryDto getComplianceSummary(String facilityId,
                                                             OffsetDateTime startDate,
                                                             OffsetDateTime endDate) {
        boolean hasRange = startDate != null || endDate != null;
        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        // RI-36: tracked = distinct patients whose events are considered by a protocol in the range
        // (an ACCEPTED, protocol-MATCHED event with event_time in [start,end]) — scoped by EVENT
        // activity, not enrolled_at, so an already-enrolled patient who is active in the window is
        // counted. Non-compliant = of those, patients with a deviation OCCURRING in the range.
        // Compliant = tracked − non-compliant (a tracked patient with no in-range deviation, enrolled
        // or not, is compliant, per the RI-36 decision).
        long totalPatients = inboundEventRepository.countDistinctPatientsWithMatchedEvents(
                facilityId, null, startDate, endDate);
        long patientsWithDeviations = deviationRepository.countDistinctNonCompliantAmongMatched(
                facilityId, null, startDate, endDate);

        long compliantPatients = Math.max(0, totalPatients - patientsWithDeviations);
        double patientComplianceRate = totalPatients > 0
                ? Math.round((double) compliantPatients / totalPatients * 1000.0) / 10.0
                : 0.0;

        Object[] activityRow = hasRange
                ? dailyKpiRepository.getFacilityActivitySummaryByDateRange(
                        startDate != null ? startDate.toLocalDate() : endDate.toLocalDate(),
                        endDate != null ? endDate.toLocalDate() : startDate.toLocalDate())
                : dailyKpiRepository.getFacilityActivitySummary();
        long totalInScope     = ((Number) activityRow[0]).longValue();
        long activeFacilities = ((Number) activityRow[1]).longValue();
        long inactiveFacilities = ((Number) activityRow[2]).longValue();
        double activeFacilityRate = ((Number) activityRow[3]).doubleValue();
        // When a single facility is selected, the activity tile reflects whether
        // THAT facility transmitted any HIE submission in the period.
        if (hasFacility) {
            boolean transmitted = inboundEventRepository.facilityTransmittedInRange(
                    facilityId, startDate, endDate);
            totalInScope = 1L;
            activeFacilities = transmitted ? 1L : 0L;
            inactiveFacilities = transmitted ? 0L : 1L;
            activeFacilityRate = transmitted ? 100.0 : 0.0;
        }

        List<PractitionerRankingDto> allPractitioners = practitionerRankingService.getRankings(
                "complianceRate", "desc", 1000, startDate, endDate, facilityId, null);
        long totalPractitioners = allPractitioners.size();
        long practitionerAbove90 = allPractitioners.stream()
                .filter(p -> p.getComplianceRate() > 90.0).count();
        long practitionerBetween75And90 = allPractitioners.stream()
                .filter(p -> p.getComplianceRate() >= 75.0 && p.getComplianceRate() <= 90.0).count();
        long practitionerBelow75 = allPractitioners.stream()
                .filter(p -> p.getComplianceRate() < 75.0).count();

        return DashboardComplianceSummaryDto.builder()
                .patients(DashboardComplianceSummaryDto.PatientComplianceDto.builder()
                        .trackedPatients(totalPatients)
                        .compliantPatients(compliantPatients)
                        .nonCompliantPatients(patientsWithDeviations)
                        .complianceRate(patientComplianceRate)
                        .build())
                .facilities(DashboardComplianceSummaryDto.FacilityComplianceDto.builder()
                        .trackedFacilities(totalInScope)
                        .activeFacilities(activeFacilities)
                        .inactiveFacilities(inactiveFacilities)
                        .activeFacilityRate(activeFacilityRate)
                        .build())
                .practitioners(DashboardComplianceSummaryDto.PractitionerComplianceDto.builder()
                        .trackedPractitioners(totalPractitioners)
                        .above90(practitionerAbove90)
                        .between75And90(practitionerBetween75And90)
                        .below75(practitionerBelow75)
                        .build())
                .build();
    }

    private void enrichFacilitiesWithHIE(List<FacilityRankingDto> facilities, Map<String, Long> facilityHIEPatients) {
        for (FacilityRankingDto f : facilities) {
            f.setPatientsFromHIE(facilityHIEPatients.getOrDefault(f.getFacilityId(), 0L));
        }
    }

    /**
     * Referrals KPI — total count of referral forms successfully received by HIE
     * plus a per-facility breakdown. Uses inbound event {@code event_time} for the
     * date range (matches the "event_time for every page metric" convention).
     * When a facility is passed, the response is scoped to that facility only.
     */
    @Cacheable(value = "metrics",
            key = "'dashboard-referrals-' + (#facilityId ?: 'all') + '-' + #startDate + '-' + #endDate")
    public ReferralsKpiDto getReferralsKpi(String facilityId,
                                            OffsetDateTime startDate,
                                            OffsetDateTime endDate) {
        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        // Facility-wise received + matched from inbound_event_logs.facility_id (event payload facility,
        // same clock as event_time). Materialise into maps for the reference-list join below.
        Map<String, Long> receivedByFacility = new LinkedHashMap<>();
        Map<String, Long> matchedByFacility  = new LinkedHashMap<>();
        for (Object[] row : inboundEventRepository.countReferralsReceivedByHIEGroupedByFacility(
                startDate, endDate)) {
            String fid = (String) row[0];
            receivedByFacility.put(fid, ((Number) row[1]).longValue());
            matchedByFacility.put(fid, ((Number) row[2]).longValue());
        }

        // Totals — single-query sums so they ignore facilities missing from the reference list
        // (defensive: a stray facility_id in an event should still be counted).
        long totalReceived = inboundEventRepository.countReferralsReceivedByHIE(
                facilityId, startDate, endDate);
        long totalMatched = inboundEventRepository.countReferralsMatched(
                facilityId, startDate, endDate);

        // Anchor to the facility reference list so every in-scope facility shows up (0 if
        // none), consistent with FacilityRankingService.getRankings().
        List<ReferralsKpiDto.FacilityReferralCountDto> byFacility = new ArrayList<>();
        for (Object[] ref : dailyKpiRepository.getFacilityReference()) {
            String fid  = (String) ref[0];
            if (hasFacility && !facilityId.equals(fid)) continue;
            String name = (String) ref[1];
            String district = ref.length > 3 ? (String) ref[3] : "";
            long received  = receivedByFacility.getOrDefault(fid, 0L);
            long matched   = matchedByFacility.getOrDefault(fid, 0L);
            byFacility.add(ReferralsKpiDto.FacilityReferralCountDto.builder()
                    .facilityId(fid)
                    .facilityName(name)
                    .district(district)
                    .count(received)
                    .compliant(matched)
                    .nonCompliant(received - matched)
                    .complianceRate(complianceRate(matched, received))
                    .build());
        }

        return ReferralsKpiDto.builder()
                .totalReferralsReceived(totalReceived)
                .compliantReferrals(totalMatched)
                .nonCompliantReferrals(totalReceived - totalMatched)
                .referralComplianceRate(complianceRate(totalMatched, totalReceived))
                .byFacility(byFacility)
                .build();
    }

    /** Compliant as a percentage of received, rounded to 1 dp; 0 when nothing received. */
    private static double complianceRate(long matched, long received) {
        if (received <= 0) return 0.0;
        return Math.round((matched * 1000.0 / received)) / 10.0;
    }
}
