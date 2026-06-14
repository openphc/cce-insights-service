package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.web.dto.ProcessingQualityDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessingQualityService {

    private final ComplianceEventLogRepository complianceEventLogRepository;

    @Cacheable(value = "analytics", key = "'processing-quality'")
    public ProcessingQualityDto getProcessingQuality(OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> statusRows = complianceEventLogRepository.countByProcessingStatus(null, startDate, endDate);
        List<Object[]> sourceRows = complianceEventLogRepository.findProcessingQualityBySource(null, null, startDate, endDate);

        long total = 0;
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Object[] row : statusRows) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            statusCounts.put(status, count);
            total += count;
        }

        Map<String, ProcessingQualityDto.StatusDetail> overall = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : statusCounts.entrySet()) {
            double pct = total > 0 ? Math.round((double) e.getValue() / total * 1000.0) / 10.0 : 0;
            overall.put(e.getKey().toLowerCase(), ProcessingQualityDto.StatusDetail.builder()
                    .count(e.getValue())
                    .percentage(pct)
                    .build());
        }

        // sourceRows: [source, processing_status, count]
        Map<String, Map<String, Long>> sourceMap = new LinkedHashMap<>();
        for (Object[] row : sourceRows) {
            String source = (String) row[0];
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            sourceMap.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(status, count);
        }

        List<ProcessingQualityDto.SourceQuality> sources = sourceMap.entrySet().stream()
                .map(e -> {
                    Map<String, Long> counts = e.getValue();
                    Map<String, ProcessingQualityDto.StatusDetail> breakdown = new LinkedHashMap<>();
                    long srcTotal = 0;
                    for (Map.Entry<String, Long> sc : counts.entrySet()) {
                        srcTotal += sc.getValue();
                    }
                    for (Map.Entry<String, Long> sc : counts.entrySet()) {
                        double pct = srcTotal > 0 ? Math.round((double) sc.getValue() / srcTotal * 1000.0) / 10.0 : 0;
                        breakdown.put(sc.getKey().toLowerCase(), ProcessingQualityDto.StatusDetail.builder()
                                .count(sc.getValue())
                                .percentage(pct)
                                .build());
                    }
                    return ProcessingQualityDto.SourceQuality.builder()
                            .source(e.getKey())
                            .totalEvents(srcTotal)
                            .breakdown(breakdown)
                            .build();
                })
                .collect(Collectors.toList());

        return ProcessingQualityDto.builder()
                .totalEvents(total)
                .overall(overall)
                .bySource(sources)
                .build();
    }
}
