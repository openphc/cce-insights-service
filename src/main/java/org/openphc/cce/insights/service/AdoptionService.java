package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.web.dto.AdoptionKpiDto;
import org.openphc.cce.insights.web.dto.FacilityReferenceDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdoptionService {

    private final DailyKpiRepository dailyKpiRepository;

    /**
     * Returns per-facility e-Buzima adoption KPIs for all in-scope facilities.
     * Facilities without mv_daily_adoption_kpis rows appear with zero actual patients.
     */
    @Cacheable(value = "analytics", key = "'adoption-kpis'")
    public List<AdoptionKpiDto> getAdoptionKpis() {
        return mergeWithReference(dailyKpiRepository.getAdoptionKpis());
    }

    /**
     * Returns adoption KPIs aggregated over a reporting period for all in-scope facilities.
     */
    @Cacheable(value = "analytics", key = "'adoption-kpis-range-' + #startDate + '-' + #endDate")
    public List<AdoptionKpiDto> getAdoptionKpisByDateRange(LocalDate startDate, LocalDate endDate) {
        return mergeWithReference(dailyKpiRepository.getAdoptionKpisByDateRange(startDate, endDate));
    }

    private List<AdoptionKpiDto> mergeWithReference(List<Object[]> adoptionRows) {
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
                result.add(toAdoptionDto(row, facilityName));
            } else {
                result.add(emptyAdoptionDto(facilityId, facilityName, expectedFromRef));
            }
        }

        result.sort(Comparator.comparingLong(AdoptionKpiDto::getReportingGap).reversed());
        return result;
    }

    private static AdoptionKpiDto toAdoptionDto(Object[] row, String facilityName) {
        // row[0]=facility_id, [1]=expected_patients_per_day, [2]=actual_patients,
        // [3]=adoption_rate_pct, [4]=reporting_gap  (facility_name resolved from facility table)
        return AdoptionKpiDto.builder()
                .facilityId((String) row[0])
                .facilityName(facilityName)
                .expectedPatientsPerDay(((Number) row[1]).longValue())
                .actualPatients(((Number) row[2]).longValue())
                .adoptionRate(((Number) row[3]).doubleValue())
                .reportingGap(((Number) row[4]).longValue())
                .build();
    }

    /** Matches mv_daily_adoption_kpis behaviour when expected baseline is zero. */
    private static AdoptionKpiDto emptyAdoptionDto(String facilityId, String facilityName, long expected) {
        return AdoptionKpiDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .expectedPatientsPerDay(expected)
                .actualPatients(0)
                .adoptionRate(expected == 0 ? 100.0 : 0.0)
                .reportingGap(expected == 0 ? 0 : expected)
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
                        .expectedPatientsPerDay(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }
}
