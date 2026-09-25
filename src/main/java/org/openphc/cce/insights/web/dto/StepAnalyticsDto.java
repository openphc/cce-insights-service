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
        private long overdueCount;       // sla_status = OVERDUE (includes steps completed late)
        private long missedCount;        // sla_status = MISSED (includes steps completed after write-off)
        private long notStartedCount;    // step_status = NOT_STARTED
        private long slaUnjudgedCount;   // sla_status not yet set
        private Double avgDaysToComplete;
        private Double medianDaysToComplete;
        private String requiredBehavior;
    }

    /** Completed steps by SLA verdict. 2.0.0 has no early/on-time split — both are MET. */
    @Data
    @Builder
    public static class TimelinessDistribution {
        private long completedOnTime;    // COMPLETED + MET
        private long completedLate;      // COMPLETED + OVERDUE | MISSED
    }
}
