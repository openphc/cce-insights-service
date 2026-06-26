package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.web.dto.AdoptionKpiDto;
import org.openphc.cce.insights.web.dto.FacilityReferenceDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdoptionService {

    private final DailyKpiRepository dailyKpiRepository;

    /**
     * Returns per-facility e-Buzima adoption KPIs for today's snapshot.
     * actualVisitsPerDay is the ceiling of the daily reporter count; reportingGapPerDay
     * is computed from that same rounded value so the three table columns always
     * reconcile.
     */
    @Cacheable(value = "analytics", key = "'adoption-kpis'")
    public List<AdoptionKpiDto> getAdoptionKpis() {
        return mergeWithReference(dailyKpiRepository.getAdoptionKpis(), 1L);
    }

    /**
     * Multi-day aggregation. Uses calendar days (not MV row count) so silent days
     * contribute zero to the daily average.
     */
    @Cacheable(value = "analytics", key = "'adoption-kpis-range-' + #startDate + '-' + #endDate")
    public List<AdoptionKpiDto> getAdoptionKpisByDateRange(LocalDate startDate, LocalDate endDate) {
        long calendarDays = Math.max(1L, ChronoUnit.DAYS.between(startDate, endDate) + 1);
        return mergeWithReference(
                dailyKpiRepository.getAdoptionKpisByDateRange(startDate, endDate),
                calendarDays);
    }

    private List<AdoptionKpiDto> mergeWithReference(List<Object[]> adoptionRows, long calendarDays) {
        // adoptionRows = [facility_id, expected_per_day, sum_actual, adoption_rate_pct]
        Map<String, Object[]> adoptionById = new LinkedHashMap<>();
        for (Object[] row : adoptionRows) {
            adoptionById.put((String) row[0], row);
        }

        List<AdoptionKpiDto> result = new ArrayList<>();
        for (Object[] ref : dailyKpiRepository.getFacilityReference()) {
            String facilityId = (String) ref[0];
            String facilityName = (String) ref[1];
            long expectedFromRef = ((Number) ref[2]).longValue();
            Object[] row = adoptionById.get(facilityId);
            if (row != null) {
                long expectedFromMv = ((Number) row[1]).longValue();
                double sumActual = ((Number) row[2]).doubleValue();
                double adoptionRate = ((Number) row[3]).doubleValue();
                result.add(buildDto(facilityId, facilityName, expectedFromMv,
                        sumActual, adoptionRate, calendarDays));
            } else {
                result.add(emptyAdoptionDto(facilityId, facilityName, expectedFromRef));
            }
        }

        // Worst under-reporters first (largest positive gap at the top).
        result.sort(Comparator.comparingLong(AdoptionKpiDto::getReportingGapPerDay).reversed());
        return result;
    }

    /**
     * Single place where the displayed values are produced:
     *   actualVisitsPerDay = ceil(sum_actual ÷ calendarDays)
     *   reportingGapPerDay = expected − actualVisitsPerDay
     * UI just renders these — no client-side rounding can drift.
     */
    private static AdoptionKpiDto buildDto(String facilityId, String facilityName,
                                            long expectedPerDay, double sumActual,
                                            double adoptionRatePct, long calendarDays) {
        long actualVisitsPerDay = (long) Math.ceil(sumActual / (double) calendarDays);
        long reportingGapPerDay = expectedPerDay == 0 ? 0L : expectedPerDay - actualVisitsPerDay;
        return AdoptionKpiDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .expectedVisitsPerDay(expectedPerDay)
                .actualVisitsPerDay(actualVisitsPerDay)
                .adoptionRate(adoptionRatePct)
                .reportingGapPerDay(reportingGapPerDay)
                .build();
    }

    /** Matches mv_daily_adoption_kpis behaviour when expected baseline is zero. */
    private static AdoptionKpiDto emptyAdoptionDto(String facilityId, String facilityName, long expected) {
        return AdoptionKpiDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .expectedVisitsPerDay(expected)
                .actualVisitsPerDay(0L)
                .adoptionRate(expected == 0 ? 100.0 : 0.0)
                .reportingGapPerDay(expected == 0 ? 0L : expected)
                .build();
    }

    /**
     * Returns the full facility list for admin screens.
     * Sourced directly from the static reference table (FINAL for dedup).
     */
    @Cacheable(value = "lookups", key = "'facility-reference'")
    public List<FacilityReferenceDto> getFacilityReference() {
        return dailyKpiRepository.getFacilityReference().stream()
                .map(row -> FacilityReferenceDto.builder()
                        .facilityId((String) row[0])
                        .facilityName((String) row[1])
                        .expectedVisitsPerDay(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }
}
