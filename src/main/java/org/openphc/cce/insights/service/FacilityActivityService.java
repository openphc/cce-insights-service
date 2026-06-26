package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.FacilityActivitySummaryDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class FacilityActivityService {

    private final DailyKpiRepository dailyKpiRepository;
    private final InboundEventRepository inboundEventRepository;

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

    /**
     * Single-facility tile: when the user selects a facility globally, Total/Active/Inactive
     * collapse to "is this one facility active in the period?" — 1 in-scope; active is 1 when
     * the facility transmitted ≥1 ACCEPTED inbound event in the range, otherwise 0.
     */
    @Cacheable(value = "analytics",
            key = "'facility-activity-single-' + #facilityId + '-' + #startDate + '-' + #endDate")
    public FacilityActivitySummaryDto getActivitySummaryForFacility(String facilityId,
                                                                     LocalDate startDate, LocalDate endDate) {
        OffsetDateTime rangeStart = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime rangeEnd   = endDate.atTime(23, 59, 59, 999_000_000).atOffset(ZoneOffset.UTC);
        boolean transmitted = inboundEventRepository.facilityTransmittedInRange(
                facilityId, rangeStart, rangeEnd);
        return FacilityActivitySummaryDto.builder()
                .totalInScope(1L)
                .activeFacilities(transmitted ? 1L : 0L)
                .inactiveFacilities(transmitted ? 0L : 1L)
                .activeFacilityRate(transmitted ? 100.0 : 0.0)
                .build();
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
