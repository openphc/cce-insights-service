package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StepAnalyticsDto {
    private UUID protocolDefinitionId;
    private String protocolCanonical;
    private List<StepMetric> steps;

    @Data
    @Builder
    public static class StepMetric {
        private String actionId;
        private long totalInstances;
        private long completedCount;
        private double completionRate;
        private TimelinessDistribution timelinessDistribution;
        private long overdueCount;
        private long missedCount;
        private long skippedCount;
        private long pendingCount;
        private Double avgDaysToComplete;
        private Double medianDaysToComplete;
        private String requiredBehavior;
    }

    @Data
    @Builder
    public static class TimelinessDistribution {
        private long early;
        private long onTime;
        private long late;
    }
}
