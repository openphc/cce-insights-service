package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.web.dto.AdoptionKpiDto;
import org.openphc.cce.insights.web.dto.FacilityReferenceDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdoptionService {

    private final DailyKpiRepository dailyKpiRepository;

    /**
     * Returns today's per-facility e-Buzima adoption KPIs from mv_daily_adoption_kpis.
     * Sorted by reporting_gap DESC (worst under-reporters first).
     */
    @Cacheable(value = "analytics", key = "'adoption-kpis'")
    public List<AdoptionKpiDto> getAdoptionKpis() {
        return mapAdoptionRows(dailyKpiRepository.getAdoptionKpis());
    }

    /**
     * Returns adoption KPIs aggregated over a reporting period per schema/07 formula:
     *   total_actual   = SUM(actual_patients across days)
     *   total_expected = expected_patients_per_day × days_in_period
     *   period_rate    = total_actual / total_expected × 100
     *   reporting_gap  = total_expected − total_actual
     */
    @Cacheable(value = "analytics", key = "'adoption-kpis-range-' + #startDate + '-' + #endDate")
    public List<AdoptionKpiDto> getAdoptionKpisByDateRange(LocalDate startDate, LocalDate endDate) {
        // [0] facility_id, [1] facility_name, [2] expected_patients_per_day,
        // [3] total_actual, [4] adoption_rate_pct (period), [5] reporting_gap (period)
        return mapAdoptionRows(dailyKpiRepository.getAdoptionKpisByDateRange(startDate, endDate));
    }

    private List<AdoptionKpiDto> mapAdoptionRows(List<Object[]> rows) {
        return rows.stream()
                .map(row -> AdoptionKpiDto.builder()
                        .facilityId((String) row[0])
                        .facilityName((String) row[1])
                        .expectedPatientsPerDay(((Number) row[2]).longValue())
                        .actualPatients(((Number) row[3]).longValue())
                        .adoptionRate(((Number) row[4]).doubleValue())
                        .reportingGap(((Number) row[5]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Returns the full facility list for admin screens.
     * Sourced directly from the static reference table (FINAL for dedup).
     */
    @Cacheable(value = "lookups", key = "'facility-reference'")
    public List<FacilityReferenceDto> getFacilityReference() {
        // [0] facility_id, [1] facility_name, [2] expected_patients_per_day
        return dailyKpiRepository.getFacilityReference().stream()
                .map(row -> FacilityReferenceDto.builder()
                        .facilityId((String) row[0])
                        .facilityName((String) row[1])
                        .expectedPatientsPerDay(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }
}
