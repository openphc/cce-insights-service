package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.config.HandoffProperties;
import org.openphc.cce.insights.config.HandoffProperties.Handoff;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.HandoffMetricDto;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deviation metrics per hand-off (configured in cce.insights.handoffs): how many hand-offs fell due in the range, how
 * many were completed on time or late, how many are outstanding past their deadline, and how long they took.
 */
@Service
@RequiredArgsConstructor
public class HandoffMetricsService {

    private final HandoffProperties properties;
    private final StepInstanceRepository stepInstanceRepository;

    /** Not cached (unlike the other dashboard metrics): the dashboard shows a hand-off as soon as its event flows. */
    public List<HandoffMetricDto> getHandoffMetrics(OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Handoff> handoffs = properties.items();
        if (handoffs.isEmpty()) return List.of();

        Map<String, Object[]> rows = new HashMap<>();
        for (Object[] row : stepInstanceRepository.findHandoffMetrics(
                handoffs.stream().map(Handoff::actionId).toList(),
                handoffs.stream().map(Handoff::fromActionId).toList(),
                startDate, endDate)) {
            rows.put(String.valueOf(row[0]), row);
        }
        // In the configured order; a hand-off with nothing due in the range shows zeros
        return handoffs.stream().map(h -> toDto(h, rows.get(h.actionId()))).toList();
    }

    static HandoffMetricDto toDto(Handoff h, Object[] row) {
        long total = count(row, 1);
        long completed = count(row, 2);
        long onTime = count(row, 3);
        long late = count(row, 4);
        long overdueOpen = count(row, 5);
        long pending = count(row, 6);
        long deviations = late + overdueOpen;
        long judged = total - pending;
        Double median = null;
        if (row != null && row[7] instanceof Number n && !Double.isNaN(n.doubleValue())) {
            median = Math.round(n.doubleValue() / 60.0 * 10.0) / 10.0;
        }
        return HandoffMetricDto.builder()
                .key(h.key())
                .label(h.label())
                .description(h.description())
                .actionId(h.actionId())
                .fromPlace(h.fromPlace())
                .toPlace(h.toPlace())
                .total(total)
                .completed(completed)
                .onTime(onTime)
                .completedLate(late)
                .overdueOpen(overdueOpen)
                .pending(pending)
                .deviations(deviations)
                .deviationRate(judged > 0 ? Math.round((double) deviations / judged * 1000.0) / 10.0 : 0.0)
                .medianMinutes(median)
                .build();
    }

    private static long count(Object[] row, int i) {
        return row != null && row[i] instanceof Number n ? n.longValue() : 0L;
    }
}
