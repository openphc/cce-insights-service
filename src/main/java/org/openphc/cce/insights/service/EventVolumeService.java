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

    @Cacheable(value = "metrics",
            key = "'vol-summary-' + (#facilityId ?: 'all') + '-' + (#source ?: 'all') + '-' + (#district ?: 'all') + '-' + #startDate + '-' + #endDate")
    public EventVolumeSummaryDto getSummary(String facilityId, String source, String district,
                                             OffsetDateTime startDate, OffsetDateTime endDate) {
        // Volume + facility/source from the event_time event-volume MV; total/matched/zeromatch/
        // duplicate/pipeline-loss from the event_time processing MV — SAME inbound event set, so the
        // rates reconcile with Total and every card is clinical (event_time) + consistent.
        List<Object[]> byFacility = inboundEventRepository.eventVolumeByFacilityAndType(facilityId, source, null, district, startDate, endDate);
        List<Object[]> bySource = inboundEventRepository.eventVolumeBySource(facilityId, district, startDate, endDate);
        Object[] proc = inboundEventRepository.eventProcessingKpis(facilityId, district, startDate, endDate);
        long totalEvents  = ((Number) proc[0]).longValue();
        long pipelineLoss = ((Number) proc[4]).longValue();

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

        // Processing breakdown from the same MV; percentages are share of total_events (so they reconcile).
        Map<String, EventVolumeSummaryDto.StatusCount> statusBreakdown = new LinkedHashMap<>();
        statusBreakdown.put("matched",   statusCount(((Number) proc[1]).longValue(), totalEvents));
        statusBreakdown.put("zeroMatch", statusCount(((Number) proc[2]).longValue(), totalEvents));
        statusBreakdown.put("duplicate", statusCount(((Number) proc[3]).longValue(), totalEvents));

        return EventVolumeSummaryDto.builder()
                .totalEvents(totalEvents)
                .processingStatusBreakdown(statusBreakdown)
                .byFacility(facilityTop)
                .bySource(sourceCounts)
                .pipelineLossCount(pipelineLoss)
                .build();
    }

    @Cacheable(value = "analytics", key = "'event-kpis'")
    public EventKpiDto getEventKpis() {
        // All-time event_time processing totals from mv_daily_event_kpis. The UI now reads the
        // date-filtered pipeline loss from getSummary(); this endpoint remains for the cumulative view.
        Object[] p = inboundEventRepository.eventProcessingKpis(null, null, null, null);
        long total = ((Number) p[0]).longValue(), matched = ((Number) p[1]).longValue();
        long zero = ((Number) p[2]).longValue(), dup = ((Number) p[3]).longValue(), loss = ((Number) p[4]).longValue();
        return EventKpiDto.builder()
                .totalEvents(total).matchedCount(matched).zeroMatchCount(zero).duplicateCount(dup)
                .matchedRatePct(total > 0 ? Math.round((double) matched / total * 1000.0) / 10.0 : 0.0)
                .zeroMatchRatePct(total > 0 ? Math.round((double) zero / total * 1000.0) / 10.0 : 0.0)
                .pipelineLossCount(loss)
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

    private EventVolumeSummaryDto.StatusCount statusCount(long count, long total) {
        double pct = total > 0 ? Math.round((double) count / total * 1000.0) / 10.0 : 0.0;
        return EventVolumeSummaryDto.StatusCount.builder().count(count).percentage(pct).build();
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

    @Cacheable(value = "metrics",
            key = "'vol-restype-' + (#facilityId ?: 'all') + '-' + (#source ?: 'all') + '-' + (#district ?: 'all') + '-' + #startDate + '-' + #endDate")
    public List<ResourceTypeCountDto> getByResourceType(String facilityId, String source, String district,
                                                        OffsetDateTime startDate, OffsetDateTime endDate) {
        return inboundEventRepository.eventVolumeByResourceType(facilityId, source, district, startDate, endDate).stream()
                .map(row -> ResourceTypeCountDto.builder()
                        .resourceType((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Cacheable(value = "metrics",
            key = "'vol-facility-' + (#facilityId ?: 'all') + '-' + (#source ?: 'all') + '-' + (#resourceType ?: 'all') + '-' + (#district ?: 'all') + '-' + #startDate + '-' + #endDate")
    public List<FacilityEventCountDto> getByFacility(String facilityId, String source, String resourceType, String district,
                                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> rows = inboundEventRepository.eventVolumeByFacilityAndType(
                facilityId, source, resourceType, district, startDate, endDate);
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

    @Cacheable(value = "metrics",
            key = "'vol-zeromatch-' + (#facilityId ?: 'all') + '-' + (#district ?: 'all') + '-' + #startDate + '-' + #endDate")
    public List<ZeroMatchEventDto> getZeroMatchEvents(String facilityId, String district,
                                                       OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> rows = inboundEventRepository.findZeroMatchEvents(facilityId, district, startDate, endDate);
        long total = rows.stream().mapToLong(r -> ((Number) r[4]).longValue()).sum();
        return rows.stream()
                .map(r -> {
                    long count = ((Number) r[4]).longValue();
                    double pct = total > 0 ? Math.round((double) count / total * 1000.0) / 10.0 : 0.0;
                    return ZeroMatchEventDto.builder()
                            .resourceType((String) r[0])
                            .code((String) r[1])
                            .category((String) r[2])
                            .facilityId((String) r[3])
                            .count(count)
                            .percentage(pct)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Cacheable(value = "metrics", key = "'vol-trends-' + #interval + '-' + #facilityId + '-' + #source + '-' + (#district ?: 'all') + '-' + #startDate + '-' + #endDate")
    public EventVolumeTrendDto getTrends(String interval, OffsetDateTime startDate,
                                          OffsetDateTime endDate, String facilityId, String source, String district) {
        String dbInterval = DateUtil.mapInterval(interval);
        // Clinical event volume from the event_time event-volume MV (consistent with the cards).
        List<Object[]> rows = inboundEventRepository.eventVolumeTrends(dbInterval, facilityId, source, district, startDate, endDate);

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
