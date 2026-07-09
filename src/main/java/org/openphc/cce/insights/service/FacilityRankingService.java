package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
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
    private final InboundEventRepository inboundEventRepository;
    private final DeviationRepository deviationRepository;

    /**
     * Returns facility rankings for all in-scope facilities from the reference table.
     * Compliance metrics (rate, deviations) come from mv_daily_facility_kpis.
     * Tracked patients use the enrolled-patient cohort via mv_patient_facility_latest.
     * Events are sourced from inbound_event_logs (status=ACCEPTED) so the column
     * matches the Active Facilities tile and the Events → By Facility table —
     * matched-event-only counting in mv_daily_facility_kpis under-reported facilities
     * that submitted accepted-but-not-compliance-matched events.
     */
    @Cacheable(value = "analytics",
            key = "'rankings-' + (#facilityId ?: 'all') + '-' + #sortBy + '-' + #order + '-' + #limit + '-' + #startDate + '-' + #endDate")
    public List<FacilityRankingDto> getRankings(UUID protocolDefinitionId, String facilityId,
                                                 LocalDate startDate, LocalDate endDate,
                                                 String sortBy, String order, int limit) {
        LocalDate today = LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : today;
        LocalDate start = startDate != null ? startDate : end;

        // Every ranking column is derived from date-scoped sources below (tracked/compliance from the
        // enrolled cohort, deviations detected in range, events accepted in range) — the all-time
        // mv_daily_facility_kpis snapshot is intentionally not read here so nothing bypasses the filter.
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

        // Deviations DATE-SCOPED to the selected range (detected_at within [start,end]) instead of the
        // MV's all-time cumulative total_deviations, so the column tracks the filter like the tracked/
        // events columns. Null bounds (no filter) = all-time, preserving the unfiltered behaviour.
        Map<String, Long> deviationsByFacility = new LinkedHashMap<>();
        for (Object[] row : deviationRepository.countDeviationsByFacility(enrollStart, enrollEnd)) {
            deviationsByFacility.put((String) row[0], ((Number) row[1]).longValue());
        }

        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        // Anchor to the facility reference list so every in-scope facility appears,
        // even when it has no row in mv_daily_facility_kpis yet.
        List<FacilityRankingDto> rankings = new ArrayList<>();
        for (Object[] ref : dailyKpiRepository.getFacilityReference()) {
            String fid = (String) ref[0];
            if (hasFacility && !facilityId.equals(fid)) continue;
            String facilityName = (String) ref[1];
            long[] patients = patientsByFacility.getOrDefault(fid, new long[]{0L, 0L});
            long deviations = deviationsByFacility.getOrDefault(fid, 0L);
            // Source totalEvents from inbound_event_logs (accepted) — aligns with
            // Active Facilities tile and Events → By Facility table.
            long inboundEvents = inboundEventRepository.countAccepted(fid, enrollStart, enrollEnd);
            rankings.add(toRankingDto(fid, facilityName, patients[0], patients[1], deviations, inboundEvents));
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

    private static FacilityRankingDto toRankingDto(String facilityId, String facilityName,
                                                    long tracked, long nonCompliant,
                                                    long activeDeviations, long inboundEvents) {
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
                .activeDeviations(activeDeviations)
                .totalEvents(inboundEvents)
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
