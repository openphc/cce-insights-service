package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.web.dto.DashboardComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.DashboardOverviewDto;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.openphc.cce.insights.web.dto.PractitionerRankingDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final InboundEventRepository inboundEventRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final DeviationRepository deviationRepository;
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

        // Top 3 and Bottom 3 facilities by compliance rate
        List<FacilityRankingDto> topFacilities = facilityRankingService.getRankings(
                startDate, endDate, "complianceRate", "desc", 3);
        List<FacilityRankingDto> bottomFacilities = facilityRankingService.getRankings(
                startDate, endDate, "complianceRate", "asc", 3);

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

    @Cacheable(value = "metrics", key = "'dashboard-compliance-summary'")
    public DashboardComplianceSummaryDto getComplianceSummary() {
        // Patient compliance
        long totalPatients = protocolInstanceRepository.findDistinctPatientIds().size();
        long patientsWithDeviations = deviationRepository.countDistinctPatientsWithDeviations();
        long compliantPatients = totalPatients - patientsWithDeviations;
        double patientComplianceRate = totalPatients > 0
                ? Math.round((double) compliantPatients / totalPatients * 1000.0) / 10.0
                : 0.0;

        // Facility compliance — get all facilities (large limit)
        List<FacilityRankingDto> allFacilities = facilityRankingService.getRankings(
                null, null, "complianceRate", "desc", 1000);
        long totalFacilities = allFacilities.size();
        long facilityAbove90 = allFacilities.stream()
                .filter(f -> f.getComplianceRate() > 90.0)
                .count();
        long facilityBetween75And90 = allFacilities.stream()
                .filter(f -> f.getComplianceRate() >= 75.0 && f.getComplianceRate() <= 90.0)
                .count();
        long facilityBelow75 = allFacilities.stream()
                .filter(f -> f.getComplianceRate() < 75.0)
                .count();

        // Practitioner compliance — get all practitioners (large limit)
        List<PractitionerRankingDto> allPractitioners = practitionerRankingService.getRankings(
                "complianceRate", "desc", 1000, null, null, null);
        long totalPractitioners = allPractitioners.size();
        long practitionerAbove90 = allPractitioners.stream()
                .filter(p -> p.getComplianceRate() > 90.0)
                .count();
        long practitionerBetween75And90 = allPractitioners.stream()
                .filter(p -> p.getComplianceRate() >= 75.0 && p.getComplianceRate() <= 90.0)
                .count();
        long practitionerBelow75 = allPractitioners.stream()
                .filter(p -> p.getComplianceRate() < 75.0)
                .count();

        return DashboardComplianceSummaryDto.builder()
                .patients(DashboardComplianceSummaryDto.PatientComplianceDto.builder()
                        .trackedPatients(totalPatients)
                        .compliantPatients(compliantPatients)
                        .nonCompliantPatients(patientsWithDeviations)
                        .complianceRate(patientComplianceRate)
                        .build())
                .facilities(DashboardComplianceSummaryDto.FacilityComplianceDto.builder()
                        .trackedFacilities(totalFacilities)
                        .above90(facilityAbove90)
                        .between75And90(facilityBetween75And90)
                        .below75(facilityBelow75)
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
