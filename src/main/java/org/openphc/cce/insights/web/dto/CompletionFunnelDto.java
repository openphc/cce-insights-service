package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CompletionFunnelDto {
    private UUID protocolDefinitionId;
    private String protocolCanonical;
    private long totalEnrollments;
    private List<FunnelStep> funnel;

    @Data
    @Builder
    public static class FunnelStep {
        private String actionId;
        private int stepOrder;
        private long reachedCount;
        private long completedCount;
        private double completionRate;
        private double dropOffRate;
    }
}
