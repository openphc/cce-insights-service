package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.DashboardOverviewDto;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
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
    private final FacilityRankingService facilityRankingService;
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
        var intel = deviationAnalyticsService.getIntelligenceSummary();
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

    private void enrichFacilitiesWithHIE(List<FacilityRankingDto> facilities, Map<String, Long> facilityHIEPatients) {
        for (FacilityRankingDto f : facilities) {
            f.setPatientsFromHIE(facilityHIEPatients.getOrDefault(f.getFacilityId(), 0L));
        }
    }
}
