package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.web.dto.DashboardComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.DashboardOverviewDto;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.openphc.cce.insights.web.dto.PractitionerRankingDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InboundEventRepository inboundEventRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final DeviationRepository deviationRepository;
    private final DailyKpiRepository dailyKpiRepository;
    private final FacilityRankingService facilityRankingService;
    private final PractitionerRankingService practitionerRankingService;
    private final DeviationAnalyticsService deviationAnalyticsService;

    @Cacheable(value = "metrics", key = "'dashboard-overview-' + #facilityId + '-' + #startDate + '-' + #endDate")
    public DashboardOverviewDto getOverview(String facilityId,
                                             OffsetDateTime startDate,
                                             OffsetDateTime endDate) {
        // Patients received via HIE (source = 'ebuzima')
        long patientsFromHIE = inboundEventRepository.countDistinctPatientSubjectsBySource(
                "ebuzima", facilityId, startDate, endDate);

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

        long totalPatients;
        long patientsWithDeviations;
        if (hasFacility) {
            // Facility-scoped cohort: enrolled patients at the selected facility
            // (always range-aware so the tile respects the date filter).
            OffsetDateTime cohortStart = hasRange ? startDate : null;
            OffsetDateTime cohortEnd   = hasRange ? endDate   : null;
            long[] counts = protocolInstanceRepository.countPatientCohortForFacility(
                    facilityId, cohortStart, cohortEnd);
            totalPatients = counts[0];
            patientsWithDeviations = counts[1];
        } else if (hasRange) {
            totalPatients = protocolInstanceRepository.countDistinctPatientsEnrolledBetween(startDate, endDate);
            patientsWithDeviations = deviationRepository.countDistinctPatientsWithDeviationsBetween(startDate, endDate);
        } else {
            totalPatients = protocolInstanceRepository.findDistinctPatientIds().size();
            patientsWithDeviations = deviationRepository.countDistinctPatientsWithDeviations();
        }

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
}
