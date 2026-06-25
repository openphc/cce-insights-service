package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
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

    /**
     * Returns facility rankings for all in-scope facilities from the reference table.
     * Metrics are cumulative across all protocols per facility (from mv_daily_facility_kpis).
     * When a date range is supplied, metrics are aggregated over that period (same as adoption KPIs).
     * Facilities without KPI rows appear with zero metrics.
     */
    @Cacheable(value = "analytics", key = "'rankings-' + #sortBy + '-' + #order + '-' + #limit + '-' + #startDate + '-' + #endDate")
    public List<FacilityRankingDto> getRankings(UUID protocolDefinitionId,
                                                 OffsetDateTime startDate, OffsetDateTime endDate,
                                                 String sortBy, String order, int limit) {
        LocalDate today = LocalDate.now();
        LocalDate end   = endDate   != null ? endDate.toLocalDate()   : today;
        LocalDate start = startDate != null ? startDate.toLocalDate() : end;

        // Match adoption: when a date range is supplied, aggregate over [start, end]
        // instead of falling back to today's snapshot.
        List<Object[]> kpis = (startDate != null || endDate != null)
                ? dailyKpiRepository.getFacilityKpisByDateRange(start, end)
                : dailyKpiRepository.getFacilityKpis();

        Map<String, Object[]> kpiByFacilityId = new LinkedHashMap<>();
        for (Object[] row : kpis) {
            kpiByFacilityId.put((String) row[0], row);
        }

        // Anchor to the facility reference list so every in-scope facility appears,
        // even when it has no row in mv_daily_facility_kpis yet.
        List<FacilityRankingDto> rankings = new ArrayList<>();
        for (Object[] ref : dailyKpiRepository.getFacilityReference()) {
            String facilityId = (String) ref[0];
            String facilityName = (String) ref[1];
            Object[] row = kpiByFacilityId.get(facilityId);
            rankings.add(row != null
                    ? toRankingDto(facilityId, facilityName, row)
                    : emptyRankingDto(facilityId, facilityName));
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

    private static FacilityRankingDto toRankingDto(String facilityId, String facilityName, Object[] row) {
        return FacilityRankingDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .totalEnrollments(((Number) row[1]).longValue())
                .compliantPatients(((Number) row[2]).longValue())
                .nonCompliantPatients(((Number) row[3]).longValue())
                .complianceRate(((Number) row[4]).doubleValue())
                .activeDeviations(((Number) row[5]).longValue())
                .totalEvents(((Number) row[6]).longValue())
                .build();
    }

    private static FacilityRankingDto emptyRankingDto(String facilityId, String facilityName) {
        return FacilityRankingDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .totalEnrollments(0)
                .compliantPatients(0)
                .nonCompliantPatients(0)
                .complianceRate(0.0)
                .activeDeviations(0)
                .totalEvents(0)
                .build();
    }
}
