package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FacilityRankingService {

    private final DailyKpiRepository dailyKpiRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;

    /**
     * Returns facility rankings for all in-scope facilities from the reference table.
     * Compliance metrics (rate, deviations, events) come from mv_daily_facility_kpis.
     * Tracked patients use the same enrolled-patient cohort as the dashboard — one
     * distinct patient per facility via mv_patient_facility_latest.
     */
    @Cacheable(value = "analytics", key = "'rankings-' + #sortBy + '-' + #order + '-' + #limit + '-' + #startDate + '-' + #endDate")
    public List<FacilityRankingDto> getRankings(UUID protocolDefinitionId,
                                                 LocalDate startDate, LocalDate endDate,
                                                 String sortBy, String order, int limit) {
        LocalDate today = LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : today;
        LocalDate start = startDate != null ? startDate : end;

        // Match adoption: when a date range is supplied, aggregate over [start, end]
        // instead of falling back to today's snapshot.
        List<Object[]> kpis = (startDate != null || endDate != null)
                ? dailyKpiRepository.getFacilityKpisByDateRange(start, end)
                : dailyKpiRepository.getFacilityKpis();

        Map<String, Object[]> kpiByFacilityId = new LinkedHashMap<>();
        for (Object[] row : kpis) {
            kpiByFacilityId.put((String) row[0], row);
        }

        Map<String, long[]> patientsByFacility = new LinkedHashMap<>();
        boolean hasDateRange = startDate != null || endDate != null;
        OffsetDateTime enrollStart = hasDateRange ? toRangeStart(startDate, end) : null;
        OffsetDateTime enrollEnd   = hasDateRange ? toRangeEnd(endDate, start) : null;
        for (Object[] row : protocolInstanceRepository.countPatientComplianceByFacility(
                enrollStart, enrollEnd)) {
            patientsByFacility.put((String) row[0], new long[]{
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()
            });
        }

        // Anchor to the facility reference list so every in-scope facility appears,
        // even when it has no row in mv_daily_facility_kpis yet.
        List<FacilityRankingDto> rankings = new ArrayList<>();
        for (Object[] ref : dailyKpiRepository.getFacilityReference()) {
            String facilityId = (String) ref[0];
            String facilityName = (String) ref[1];
            long[] patients = patientsByFacility.getOrDefault(facilityId, new long[]{0L, 0L});
            Object[] row = kpiByFacilityId.get(facilityId);
            rankings.add(row != null
                    ? toRankingDto(facilityId, facilityName, row, patients[0], patients[1])
                    : toRankingDtoWithoutKpis(facilityId, facilityName, patients[0], patients[1]));
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

    private static FacilityRankingDto toRankingDto(String facilityId, String facilityName, Object[] row,
                                                    long tracked, long nonCompliant) {
        long compliant = Math.max(0, tracked - nonCompliant);
        double complianceRate = tracked > 0
                ? Math.round((double) compliant / tracked * 1000.0) / 10.0
                : 0.0;
        return FacilityRankingDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .totalEnrollments(tracked)
                .compliantPatients(compliant)
                .nonCompliantPatients(nonCompliant)
                .complianceRate(complianceRate)
                .activeDeviations(((Number) row[5]).longValue())
                .totalEvents(((Number) row[6]).longValue())
                .build();
    }

    private static FacilityRankingDto toRankingDtoWithoutKpis(String facilityId, String facilityName,
                                                             long tracked, long nonCompliant) {
        long compliant = Math.max(0, tracked - nonCompliant);
        double complianceRate = tracked > 0
                ? Math.round((double) compliant / tracked * 1000.0) / 10.0
                : 0.0;
        return FacilityRankingDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .totalEnrollments(tracked)
                .compliantPatients(compliant)
                .nonCompliantPatients(nonCompliant)
                .complianceRate(complianceRate)
                .activeDeviations(0)
                .totalEvents(0)
                .build();
    }

    private static OffsetDateTime toRangeStart(LocalDate startDate, LocalDate fallbackEnd) {
        if (startDate == null && fallbackEnd == null) return null;
        LocalDate date = startDate != null ? startDate : fallbackEnd;
        return date.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    }

    private static OffsetDateTime toRangeEnd(LocalDate endDate, LocalDate fallbackStart) {
        if (endDate == null && fallbackStart == null) return null;
        LocalDate date = endDate != null ? endDate : fallbackStart;
        return date.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
    }
}
