package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.web.dto.FacilityActivitySummaryDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FacilityActivityService {

    private final DailyKpiRepository dailyKpiRepository;

    @Cacheable(value = "analytics", key = "'facility-activity-summary'")
    public FacilityActivitySummaryDto getActivitySummary() {
        Object[] row = dailyKpiRepository.getFacilityActivitySummary();
        return toDto(row);
    }

    @Cacheable(value = "analytics", key = "'facility-activity-range-' + #startDate + '-' + #endDate")
    public FacilityActivitySummaryDto getActivitySummaryByDateRange(LocalDate startDate, LocalDate endDate) {
        Object[] row = dailyKpiRepository.getFacilityActivitySummaryByDateRange(startDate, endDate);
        return toDto(row);
    }

    private FacilityActivitySummaryDto toDto(Object[] row) {
        return FacilityActivitySummaryDto.builder()
                .totalInScope(((Number) row[0]).longValue())
                .activeFacilities(((Number) row[1]).longValue())
                .inactiveFacilities(((Number) row[2]).longValue())
                .activeFacilityRate(((Number) row[3]).doubleValue())
                .build();
    }
}
