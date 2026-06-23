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

    @Cacheable(value = "metrics", key = "'vol-practitioner-' + #startDate + '-' + #endDate")
    public List<PractitionerEventCountDto> getByPractitioner(OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> rows = complianceEventLogRepository.countByPractitioner(null, startDate, endDate);
        // rows: [practitioner_ref, practitioner_display, resource_type, count]
        Map<String, List<Object[]>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            grouped.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add(row);
        }
        return grouped.entrySet().stream().map(e -> {
            List<ResourceTypeCountDto> byType = e.getValue().stream()
                    .map(r -> ResourceTypeCountDto.builder()
                            .resourceType((String) r[2])
                            .count(((Number) r[3]).longValue())
                            .build())
                    .collect(Collectors.toList());
            long total = byType.stream().mapToLong(ResourceTypeCountDto::getCount).sum();
            String display = e.getValue().get(0)[1] != null ? (String) e.getValue().get(0)[1] : null;
            return PractitionerEventCountDto.builder()
                    .practitionerRef(e.getKey())
                    .practitionerDisplay(display)
                    .totalEvents(total)
                    .byResourceType(byType)
                    .build();
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "metrics", key = "'vol-source-' + #startDate + '-' + #endDate")
    public List<SourceSystemCountDto> getBySource(OffsetDateTime startDate, OffsetDateTime endDate) {
        // Source counts from inbound_event — shows ALL events received per source with status breakdown
        List<Object[]> rows = inboundEventRepository.countBySourceAndStatus(null, startDate, endDate);
        // rows: [source, status, count]
        Map<String, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String source = (String) row[0];
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            grouped.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(status, count);
        }
        return grouped.entrySet().stream().map(e -> {
            Map<String, Long> statusCounts = e.getValue();
            long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();
            // Map status counts as ResourceTypeCountDto (reusing DTO — status acts as category)
            List<ResourceTypeCountDto> byStatus = statusCounts.entrySet().stream()
                    .map(s -> ResourceTypeCountDto.builder()
                            .resourceType(s.getKey())
                            .count(s.getValue())
                            .build())
                    .collect(Collectors.toList());
            return SourceSystemCountDto.builder()
                    .source(e.getKey())
                    .totalEvents(total)
                    .byResourceType(byStatus)
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

    public SourceComparisonDto compareSourceSystems(String sourceA, String sourceB,
                                                     long windowSeconds, String facilityId,
                                                     OffsetDateTime startDate, OffsetDateTime endDate,
                                                     int sampleLimit) {
        // Source comparison uses inbound_event — captures ALL events received, not just compliance-matched
        List<Object[]> overlapRows = inboundEventRepository.findOverlappingEvents(
                sourceA, sourceB, windowSeconds, facilityId, startDate, endDate);

        // Unique to sourceA
        List<Object[]> uniqueARows = inboundEventRepository.findUniqueToSource(
                sourceA, sourceB, windowSeconds, facilityId, startDate, endDate);

        // Unique to sourceB
        List<Object[]> uniqueBRows = inboundEventRepository.findUniqueToSource(
                sourceB, sourceA, windowSeconds, facilityId, startDate, endDate);

        // Sample overlapping events for drill-down
        List<Object[]> sampleRows = inboundEventRepository.findOverlappingEventSamples(
                sourceA, sourceB, windowSeconds, facilityId, startDate, endDate, sampleLimit);

        long overlapCount = overlapRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long uniqueACount = uniqueARows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long uniqueBCount = uniqueBRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long totalA = overlapCount + uniqueACount;
        long totalB = overlapCount + uniqueBCount;

        double overlapPctA = totalA > 0 ? Math.round((double) overlapCount / totalA * 1000.0) / 10.0 : 0;
        double overlapPctB = totalB > 0 ? Math.round((double) overlapCount / totalB * 1000.0) / 10.0 : 0;

        List<SourceComparisonDto.OverlapSample> samples = sampleRows.stream().map(row -> {
            OffsetDateTime tsA = DateUtil.toOffsetDateTime(row[4]);
            OffsetDateTime tsB = DateUtil.toOffsetDateTime(row[5]);
            return SourceComparisonDto.OverlapSample.builder()
                    .eventAId((UUID) row[0])
                    .eventBId((UUID) row[1])
                    .subject((String) row[2])
                    .resourceType((String) row[3])
                    .eventTimeA(tsA)
                    .eventTimeB(tsB)
                    .timeDiffSeconds(((Number) row[6]).doubleValue())
                    .build();
        }).collect(Collectors.toList());

        return SourceComparisonDto.builder()
                .sourceA(sourceA)
                .sourceB(sourceB)
                .matchWindowSeconds(windowSeconds)
                .sourceASummary(SourceComparisonDto.SourceSummary.builder()
                        .source(sourceA)
                        .totalEvents(totalA)
                        .uniqueEvents(uniqueACount)
                        .overlappingEvents(overlapCount)
                        .overlapPercentage(overlapPctA)
                        .uniqueByResourceType(toResourceTypeCounts(uniqueARows))
                        .build())
                .sourceBSummary(SourceComparisonDto.SourceBSummary.builder()
                        .source(sourceB)
                        .totalEvents(totalB)
                        .uniqueEvents(uniqueBCount)
                        .overlappingEvents(overlapCount)
                        .overlapPercentage(overlapPctB)
                        .uniqueByResourceType(toResourceTypeCounts(uniqueBRows))
                        .build())
                .overlap(SourceComparisonDto.OverlapSummary.builder()
                        .totalOverlappingEvents(overlapCount)
                        .byResourceType(toResourceTypeCounts(overlapRows))
                        .build())
                .samples(samples)
                .build();
    }

    private List<SourceComparisonDto.ResourceTypeCount> toResourceTypeCounts(List<Object[]> rows) {
        return rows.stream()
                .map(r -> SourceComparisonDto.ResourceTypeCount.builder()
                        .resourceType((String) r[0])
                        .count(((Number) r[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }
}
