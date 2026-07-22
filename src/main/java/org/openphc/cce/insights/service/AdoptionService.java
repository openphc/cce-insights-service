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
            String district = ref.length > 3 ? (String) ref[3] : "";
            Object[] row = adoptionById.get(facilityId);
            if (row != null) {
                long expectedFromMv = ((Number) row[1]).longValue();
                double sumActual = ((Number) row[2]).doubleValue();
                double adoptionRate = ((Number) row[3]).doubleValue();
                result.add(buildDto(facilityId, facilityName, district, expectedFromMv,
                        sumActual, adoptionRate, calendarDays));
            } else {
                result.add(emptyAdoptionDto(facilityId, facilityName, district, expectedFromRef, calendarDays));
            }
        }

        // Highest adoption rate first.
        result.sort(Comparator.comparingDouble(AdoptionKpiDto::getAdoptionRate).reversed());
        return result;
    }

    /**
     * Single place where the displayed values are produced (RI-33 — over the whole period):
     *   expectedVisits = baseline/day × calendarDays
     *   actualVisits   = round(sum_actual over the range)
     *   reportingGap   = expectedVisits − actualVisits
     * UI just renders these — no client-side rounding can drift.
     */
    private static AdoptionKpiDto buildDto(String facilityId, String facilityName, String district,
                                            long expectedPerDay, double sumActual,
                                            double adoptionRatePct, long calendarDays) {
        long expectedVisits = expectedPerDay * calendarDays;
        long actualVisits   = Math.round(sumActual);
        long reportingGap   = expectedPerDay == 0 ? 0L : expectedVisits - actualVisits;
        // No expected baseline AND no actual visits → 0% (an inactive/unbaselined facility hasn't
        // "adopted"); never report a vacuous 100%.
        double adoptionRate = (expectedPerDay == 0 && actualVisits == 0) ? 0.0 : adoptionRatePct;
        return AdoptionKpiDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .district(district)
                .expectedVisits(expectedVisits)
                .actualVisits(actualVisits)
                .adoptionRate(adoptionRate)
                .reportingGap(reportingGap)
                .build();
    }

    /** Matches mv_daily_adoption_kpis behaviour when a facility has no adoption row (RI-33: over range). */
    private static AdoptionKpiDto emptyAdoptionDto(String facilityId, String facilityName,
                                                   String district, long expectedPerDay, long calendarDays) {
        long expectedVisits = expectedPerDay * calendarDays;
        return AdoptionKpiDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .district(district)
                .expectedVisits(expectedVisits)
                .actualVisits(0L)
                // No adoption row → 0 actual visits → 0% (whether or not an expected baseline exists);
                // never a vacuous 100% for a facility with no expected baseline.
                .adoptionRate(0.0)
                .reportingGap(expectedPerDay == 0 ? 0L : expectedVisits)
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
