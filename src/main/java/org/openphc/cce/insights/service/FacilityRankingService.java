package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FacilityRankingService {

    private final DailyKpiRepository dailyKpiRepository;
    private final ComplianceEventLogRepository complianceEventLogRepository;

    /**
     * Returns facility rankings from mv_daily_facility_kpis.
     *
     * When startDate/endDate span a historical range (endDate before today):
     *   compliance data = argMax per facility (latest state in range)
     *   event_count     = SUM across all days in range
     * When endDate is today or no dates given: reads today's snapshot.
     */
    @Cacheable(value = "analytics", key = "'rankings-' + #protocolDefinitionId + '-' + #sortBy + '-' + #order + '-' + #limit + '-' + #startDate + '-' + #endDate")
    public List<FacilityRankingDto> getRankings(UUID protocolDefinitionId,
                                                 OffsetDateTime startDate, OffsetDateTime endDate,
                                                 String sortBy, String order, int limit) {
        LocalDate today = LocalDate.now();
        LocalDate end   = endDate   != null ? endDate.toLocalDate()   : today;
        LocalDate start = startDate != null ? startDate.toLocalDate() : end;

        // Use date-range query when a historical range is given; today's snapshot otherwise.
        List<Object[]> kpis = end.isBefore(today)
                ? dailyKpiRepository.getFacilityKpisByDateRange(start, end)
                : dailyKpiRepository.getFacilityKpis();

        Map<String, String> facilityNameMap = new LinkedHashMap<>();
        for (Object[] row : complianceEventLogRepository.findFacilityNames()) {
            facilityNameMap.put((String) row[0], (String) row[1]);
        }

        // mv_daily_facility_kpis columns:
        // [0] facility_id, [1] tracked_patients, [2] compliant_patients,
        // [3] non_compliant_patients, [4] compliance_rate_pct, [5] total_deviations, [6] event_count
        List<FacilityRankingDto> rankings = new ArrayList<>();
        for (Object[] row : kpis) {
            String facilityId = (String) row[0];
            rankings.add(FacilityRankingDto.builder()
                    .facilityId(facilityId)
                    .facilityName(facilityNameMap.getOrDefault(facilityId, facilityId))
                    .totalEnrollments(((Number) row[1]).longValue())
                    .compliantPatients(((Number) row[2]).longValue())
                    .nonCompliantPatients(((Number) row[3]).longValue())
                    .complianceRate(((Number) row[4]).doubleValue())
                    .activeDeviations(((Number) row[5]).longValue())
                    .totalEvents(((Number) row[6]).longValue())
                    .build());
        }

        // Filter to only facilities that have patients enrolled in the given protocol
        if (protocolDefinitionId != null) {
            Set<String> allowedFacilities = new HashSet<>(
                    complianceEventLogRepository.findFacilityIdsByProtocol(protocolDefinitionId));
            rankings.removeIf(r -> !allowedFacilities.contains(r.getFacilityId()));
        }

        Comparator<FacilityRankingDto> comparator = switch (sortBy != null ? sortBy : "complianceRate") {
            case "complianceRate"  -> Comparator.comparingDouble(FacilityRankingDto::getComplianceRate);
            case "deviationCount"  -> Comparator.comparingLong(FacilityRankingDto::getActiveDeviations);
            case "eventVolume", "totalEvents" -> Comparator.comparingLong(FacilityRankingDto::getTotalEvents);
            default -> Comparator.comparingDouble(FacilityRankingDto::getComplianceRate);
        };
        if ("desc".equalsIgnoreCase(order)) comparator = comparator.reversed();

        rankings.sort(comparator);

        int rank = 1;
        List<FacilityRankingDto> ranked = new ArrayList<>();
        for (FacilityRankingDto dto : rankings) {
            if (rank > limit) break;
            ranked.add(FacilityRankingDto.builder()
                    .rank(rank++)
                    .facilityId(dto.getFacilityId())
                    .facilityName(dto.getFacilityName())
                    .totalEnrollments(dto.getTotalEnrollments())
                    .compliantPatients(dto.getCompliantPatients())
                    .nonCompliantPatients(dto.getNonCompliantPatients())
                    .complianceRate(dto.getComplianceRate())
                    .activeDeviations(dto.getActiveDeviations())
                    .totalEvents(dto.getTotalEvents())
                    .build());
        }
        return ranked;
    }
}
