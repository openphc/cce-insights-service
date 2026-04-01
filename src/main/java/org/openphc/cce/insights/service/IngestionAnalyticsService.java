package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngestionAnalyticsService {

    private final InboundEventRepository inboundEventRepository;

    @Cacheable(value = "metrics", key = "'funnel-' + #facilityId + '-' + #source")
    public IngestionFunnelDto getIngestionFunnel(String facilityId, String source,
                                                  OffsetDateTime startDate, OffsetDateTime endDate,
                                                  String interval) {
        List<Object[]> statusRows = inboundEventRepository.countByStatus(facilityId, source, startDate, endDate);

        long total = 0;
        long accepted = 0;
        long rejected = 0;
        long duplicate = 0;
        List<IngestionFunnelDto.StatusBreakdown> breakdown = new ArrayList<>();

        for (Object[] row : statusRows) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            total += count;
            switch (status) {
                case "ACCEPTED" -> accepted = count;
                case "REJECTED" -> rejected = count;
                case "DUPLICATE" -> duplicate = count;
            }
        }

        for (Object[] row : statusRows) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double pct = total > 0 ? Math.round((double) count / total * 1000.0) / 10.0 : 0;
            breakdown.add(IngestionFunnelDto.StatusBreakdown.builder()
                    .status(status)
                    .count(count)
                    .percentage(pct)
                    .build());
        }

        List<IngestionFunnelDto.TrendPoint> trends = null;
        if (interval != null) {
            String dbInterval = DateUtil.mapInterval(interval);
            List<Object[]> trendRows = inboundEventRepository.findIngestionTrends(
                    dbInterval, facilityId, source, startDate, endDate);
            trends = buildTrendPoints(trendRows);
        }

        return IngestionFunnelDto.builder()
                .totalReceived(total)
                .accepted(accepted)
                .rejected(rejected)
                .duplicate(duplicate)
                .acceptanceRate(total > 0 ? Math.round((double) accepted / total * 1000.0) / 10.0 : 0)
                .rejectionRate(total > 0 ? Math.round((double) rejected / total * 1000.0) / 10.0 : 0)
                .duplicateRate(total > 0 ? Math.round((double) duplicate / total * 1000.0) / 10.0 : 0)
                .breakdown(breakdown)
                .trends(trends)
                .build();
    }

    @Cacheable(value = "metrics", key = "'rejections-' + #facilityId + '-' + #source")
    public RejectionAnalyticsDto getRejectionAnalytics(String facilityId, String source,
                                                        OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> reasonRows = inboundEventRepository.countByRejectionReason(
                facilityId, source, startDate, endDate);

        long totalRejected = reasonRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        List<RejectionAnalyticsDto.ReasonBreakdown> byReason = reasonRows.stream()
                .map(row -> {
                    long count = ((Number) row[1]).longValue();
                    double pct = totalRejected > 0 ? Math.round((double) count / totalRejected * 1000.0) / 10.0 : 0;
                    return RejectionAnalyticsDto.ReasonBreakdown.builder()
                            .reason((String) row[0])
                            .count(count)
                            .percentage(pct)
                            .build();
                })
                .collect(Collectors.toList());

        // Per-source rejection details
        List<Object[]> sourceStatusRows = inboundEventRepository.countBySourceAndStatus(
                facilityId, startDate, endDate);
        List<Object[]> sourceReasonRows = inboundEventRepository.countBySourceAndRejectionReason(
                facilityId, startDate, endDate);

        // Build source totals: source -> {status -> count}
        Map<String, Map<String, Long>> sourceStatusMap = new LinkedHashMap<>();
        for (Object[] row : sourceStatusRows) {
            String src = (String) row[0];
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            sourceStatusMap.computeIfAbsent(src, k -> new LinkedHashMap<>()).put(status, count);
        }

        // Build source rejection reasons: source -> [{reason, count}]
        Map<String, List<Object[]>> sourceReasonMap = new LinkedHashMap<>();
        for (Object[] row : sourceReasonRows) {
            sourceReasonMap.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add(row);
        }

        List<RejectionAnalyticsDto.SourceRejection> bySource = sourceStatusMap.entrySet().stream()
                .map(e -> {
                    Map<String, Long> statusCounts = e.getValue();
                    long srcTotal = statusCounts.values().stream().mapToLong(Long::longValue).sum();
                    long srcRejected = statusCounts.getOrDefault("REJECTED", 0L);
                    double rejRate = srcTotal > 0 ? Math.round((double) srcRejected / srcTotal * 1000.0) / 10.0 : 0;

                    List<RejectionAnalyticsDto.ReasonBreakdown> reasons = Optional.ofNullable(sourceReasonMap.get(e.getKey()))
                            .orElse(List.of()).stream()
                            .map(row -> RejectionAnalyticsDto.ReasonBreakdown.builder()
                                    .reason((String) row[1])
                                    .count(((Number) row[2]).longValue())
                                    .percentage(srcRejected > 0 ? Math.round((double) ((Number) row[2]).longValue() / srcRejected * 1000.0) / 10.0 : 0)
                                    .build())
                            .collect(Collectors.toList());

                    return RejectionAnalyticsDto.SourceRejection.builder()
                            .source(e.getKey())
                            .totalEvents(srcTotal)
                            .rejectedEvents(srcRejected)
                            .rejectionRate(rejRate)
                            .topReasons(reasons)
                            .build();
                })
                .filter(sr -> sr.getRejectedEvents() > 0)
                .collect(Collectors.toList());

        return RejectionAnalyticsDto.builder()
                .totalRejected(totalRejected)
                .byReason(byReason)
                .bySource(bySource)
                .build();
    }

    @Cacheable(value = "metrics", key = "'quality-' + #facilityId")
    public SourceDataQualityDto getSourceDataQuality(String facilityId,
                                                      OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> rows = inboundEventRepository.countBySourceAndStatus(facilityId, startDate, endDate);

        // source -> {status -> count}
        Map<String, Map<String, Long>> sourceMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String source = (String) row[0];
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            sourceMap.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(status, count);
        }

        List<SourceDataQualityDto.SourceQuality> sources = sourceMap.entrySet().stream()
                .map(e -> {
                    Map<String, Long> sc = e.getValue();
                    long total = sc.values().stream().mapToLong(Long::longValue).sum();
                    long acc = sc.getOrDefault("ACCEPTED", 0L);
                    long rej = sc.getOrDefault("REJECTED", 0L);
                    long dup = sc.getOrDefault("DUPLICATE", 0L);
                    return SourceDataQualityDto.SourceQuality.builder()
                            .source(e.getKey())
                            .totalEvents(total)
                            .accepted(acc)
                            .rejected(rej)
                            .duplicate(dup)
                            .acceptanceRate(total > 0 ? Math.round((double) acc / total * 1000.0) / 10.0 : 0)
                            .rejectionRate(total > 0 ? Math.round((double) rej / total * 1000.0) / 10.0 : 0)
                            .duplicateRate(total > 0 ? Math.round((double) dup / total * 1000.0) / 10.0 : 0)
                            .build();
                })
                .collect(Collectors.toList());

        return SourceDataQualityDto.builder().sources(sources).build();
    }

    @Cacheable(value = "metrics", key = "'pipeline-loss-' + #facilityId")
    public PipelineLossDto getPipelineLoss(String facilityId,
                                            OffsetDateTime startDate, OffsetDateTime endDate) {
        long totalAccepted = inboundEventRepository.countAccepted(facilityId, startDate, endDate);
        long lostCount = inboundEventRepository.countPipelineLoss(facilityId, startDate, endDate);
        long inEventLog = totalAccepted - lostCount;

        List<Object[]> bySourceRows = inboundEventRepository.findPipelineLossBySource(
                facilityId, startDate, endDate);
        List<PipelineLossDto.SourceLoss> bySource = bySourceRows.stream()
                .map(row -> PipelineLossDto.SourceLoss.builder()
                        .source((String) row[0])
                        .lostEvents(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        return PipelineLossDto.builder()
                .totalAcceptedByCollector(totalAccepted)
                .totalInComplianceEventLog(inEventLog)
                .lostEvents(lostCount)
                .lossRate(totalAccepted > 0 ? Math.round((double) lostCount / totalAccepted * 1000.0) / 10.0 : 0)
                .bySource(bySource)
                .build();
    }

    private List<IngestionFunnelDto.TrendPoint> buildTrendPoints(List<Object[]> rows) {
        // rows: [period, status, count]
        Map<String, Map<String, Long>> periodMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String period = DateUtil.extractDate(row[0]);
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            periodMap.computeIfAbsent(period, k -> new LinkedHashMap<>()).put(status, count);
        }
        return periodMap.entrySet().stream().map(e -> {
            long total = e.getValue().values().stream().mapToLong(Long::longValue).sum();
            return IngestionFunnelDto.TrendPoint.builder()
                    .period(e.getKey())
                    .byStatus(e.getValue())
                    .total(total)
                    .build();
        }).collect(Collectors.toList());
    }
}
