package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventKpiDto {
    private long totalEvents;
    private long matchedCount;
    private long zeroMatchCount;
    private long duplicateCount;
    private double matchedRatePct;
    private double zeroMatchRatePct;
    private long pipelineLossCount;
}
