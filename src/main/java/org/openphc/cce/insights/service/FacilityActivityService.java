package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.FacilityActivityItemDto;
import org.openphc.cce.insights.web.dto.FacilityActivitySummaryDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

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

    /**
     * Drill-down list behind the Active/Inactive cards: every in-scope facility with its
     * active/inactive flag, district, and last-activity day for [startDate, endDate]. The UI
     * partitions by {@code active}; counts reconcile with the summary card.
     */
    @Cacheable(value = "analytics", key = "'facility-activity-detail-' + #startDate + '-' + #endDate")
    public List<FacilityActivityItemDto> getFacilityActivityDetail(LocalDate startDate, LocalDate endDate) {
        return dailyKpiRepository.getFacilityActivityDetail(startDate, endDate).stream()
                .map(r -> FacilityActivityItemDto.builder()
                        .facilityId((String) r[0])
                        .facilityName((String) r[1])
                        .district((String) r[2])
                        .lastActivity((String) r[3])
                        .active(((Number) r[4]).intValue() == 1)
                        .build())
                // Display order: district A→Z, then facility A→Z, then most-recent activity first.
                // (facility is unique per row, so lastActivity is only a formal tie-breaker.)
                .sorted(Comparator
                        .comparing((FacilityActivityItemDto d) -> d.getDistrict() == null ? "" : d.getDistrict(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(d -> d.getFacilityName() == null ? "" : d.getFacilityName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(FacilityActivityItemDto::getLastActivity,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
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
