package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventVolumeService {

    private final ComplianceEventLogRepository complianceEventLogRepository;
    private final InboundEventRepository inboundEventRepository;
    private final DailyKpiRepository dailyKpiRepository;

    @Cacheable(value = "metrics", key = "'vol-summary-' + #startDate + '-' + #endDate")
    public EventVolumeSummaryDto getSummary(OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> byFacility = complianceEventLogRepository.countByFacility(startDate, endDate);
        List<Object[]> byResourceType = complianceEventLogRepository.countByResourceType(null, null, startDate, endDate);
        // Source counts from inbound_event — captures ALL received events, not just compliance-matched
        List<Object[]> bySource = inboundEventRepository.countBySource(null, startDate, endDate);
        List<Object[]> byProcessingStatus = complianceEventLogRepository.countByProcessingStatus(null, startDate, endDate);

        long totalEvents = byResourceType.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        List<EventVolumeSummaryDto.FacilityCount> facilityTop = new ArrayList<>();
        Map<String, Long> facilityTotals = new LinkedHashMap<>();
        for (Object[] r : byFacility) {
            String fid = (String) r[0];
            long cnt = ((Number) r[2]).longValue();
            facilityTotals.merge(fid, cnt, Long::sum);
        }
        facilityTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> facilityTop.add(EventVolumeSummaryDto.FacilityCount.builder()
                        .facilityId(e.getKey())
                        .count(e.getValue())
                        .build()));

        List<EventVolumeSummaryDto.SourceCount> sourceCounts = bySource.stream()
                .map(r -> EventVolumeSummaryDto.SourceCount.builder()
                        .source((String) r[0])
                        .count(((Number) r[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        // Build processing status breakdown with counts and percentages
        Map<String, EventVolumeSummaryDto.StatusCount> statusBreakdown = buildProcessingStatusBreakdown(byProcessingStatus);

        return EventVolumeSummaryDto.builder()
                .totalEvents(totalEvents)
                .processingStatusBreakdown(statusBreakdown)
                .byFacility(facilityTop)
                .bySource(sourceCounts)
                .build();
    }

    @Cacheable(value = "analytics", key = "'event-kpis'")
    public EventKpiDto getEventKpis() {
        // Reads from mv_daily_event_kpis (all-time totals, refreshed every 30 min).
        // Use this for the events page header cards; use getSummary() for date-filtered breakdowns.
        Object[] row = dailyKpiRepository.getEventKpis();
        return EventKpiDto.builder()
                .totalEvents(((Number) row[0]).longValue())
                .matchedCount(((Number) row[1]).longValue())
                .zeroMatchCount(((Number) row[2]).longValue())
                .duplicateCount(((Number) row[3]).longValue())
                .matchedRatePct(((Number) row[4]).doubleValue())
                .zeroMatchRatePct(((Number) row[5]).doubleValue())
                .pipelineLossCount(((Number) row[6]).longValue())
                .build();
    }

    private Map<String, EventVolumeSummaryDto.StatusCount> buildProcessingStatusBreakdown(List<Object[]> rows) {
        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        EventVolumeSummaryDto.StatusCount zero = EventVolumeSummaryDto.StatusCount.builder()
                .count(0).percentage(0.0).build();
        Map<String, EventVolumeSummaryDto.StatusCount> breakdown = new LinkedHashMap<>();
        breakdown.put("matched", zero);
        breakdown.put("zeroMatch", zero);
        breakdown.put("duplicate", zero);
        for (Object[] row : rows) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double percentage = total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0.0;
            String key = mapStatusKey(status);
            breakdown.put(key, EventVolumeSummaryDto.StatusCount.builder()
                    .count(count)
                    .percentage(percentage)
                    .build());
        }
        return breakdown;
    }

    private String mapStatusKey(String dbStatus) {
        if (dbStatus == null) return "unknown";
        switch (dbStatus.toUpperCase()) {
            case "MATCHED": return "matched";
            case "ZERO_MATCH": return "zeroMatch";
            case "DUPLICATE": return "duplicate";
            default: return dbStatus.toLowerCase();
        }
    }

    @Cacheable(value = "metrics", key = "'vol-restype-' + #startDate + '-' + #endDate")
    public List<ResourceTypeCountDto> getByResourceType(OffsetDateTime startDate, OffsetDateTime endDate) {
        return complianceEventLogRepository.countByResourceType(null, null, startDate, endDate).stream()
                .map(row -> ResourceTypeCountDto.builder()
                        .resourceType((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Cacheable(value = "metrics", key = "'vol-facility-' + #startDate + '-' + #endDate")
    public List<FacilityEventCountDto> getByFacility(OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> rows = complianceEventLogRepository.countByFacility(startDate, endDate);
        // rows: [facility_id, resource_type, count] — aggregate by facility
        Map<String, List<Object[]>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            grouped.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add(row);
        }
        return grouped.entrySet().stream().map(e -> {
            List<ResourceTypeCountDto> byType = e.getValue().stream()
                    .map(r -> ResourceTypeCountDto.builder()
                            .resourceType((String) r[1])
                            .count(((Number) r[2]).longValue())
                            .build())
                    .collect(Collectors.toList());
            long total = byType.stream().mapToLong(ResourceTypeCountDto::getCount).sum();
            return FacilityEventCountDto.builder()
                    .facilityId(e.getKey())
                    .totalEvents(total)
                    .byResourceType(byType)
                    .build();
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "metrics", key = "'vol-trends-' + #interval + '-' + #facilityId + '-' + #source + '-' + #startDate + '-' + #endDate")
    public EventVolumeTrendDto getTrends(String interval, OffsetDateTime startDate,
                                          OffsetDateTime endDate, String facilityId, String source) {
        String dbInterval = DateUtil.mapInterval(interval);
        // When filtering by source, use inbound_event to capture ALL received events (not just compliance-matched)
        List<Object[]> rows = (source != null && !source.isBlank())
                ? inboundEventRepository.findEventTrends(dbInterval, facilityId, source, startDate, endDate)
                : complianceEventLogRepository.findEventTrends(dbInterval, facilityId, source, null, startDate, endDate);

        // rows: [period, resource_type, count] — aggregate by period
        Map<String, Map<String, Long>> periodMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String period = DateUtil.extractDate(row[0]);
            String resourceType = (String) row[1];
            long count = ((Number) row[2]).longValue();
            periodMap.computeIfAbsent(period, k -> new LinkedHashMap<>()).put(resourceType, count);
        }

        List<EventVolumeTrendDto.TrendPoint> trends = periodMap.entrySet().stream().map(e -> {
            long total = e.getValue().values().stream().mapToLong(Long::longValue).sum();
            return EventVolumeTrendDto.TrendPoint.builder()
                    .period(e.getKey())
                    .total(total)
                    .byResourceType(e.getValue())
                    .build();
        }).collect(Collectors.toList());

        return EventVolumeTrendDto.builder()
                .interval(interval != null ? interval : "weekly")
                .trends(trends)
                .build();
    }
}
