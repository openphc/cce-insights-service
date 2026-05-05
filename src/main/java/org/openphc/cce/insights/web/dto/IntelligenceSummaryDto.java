package org.openphc.cce.insights.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class IntelligenceSummaryDto {
    private long total;
    private long delivered;
    private long failed;
    private long pending;
    private double successRate;
    private Double avgLatencySeconds;
    private List<StatusBreakdown> byStatus;
    private List<StatusBreakdown> byActionType;
    private List<StatusBreakdown> bySeverity;
    private List<DestinationBreakdown> byDestination;
    private List<ActiveAdaptor> activeAdaptors;

    @Data
    @Builder
    @AllArgsConstructor
    public static class StatusBreakdown {
        private String label;
        private long count;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class DestinationBreakdown {
        private String destination;
        private long count;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ActiveAdaptor {
        private String name;
        private String status;
        private List<String> destinations;
    }
}
