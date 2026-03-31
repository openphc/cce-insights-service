package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeviationResolutionDto {
    private long totalOverdueDeviations;
    private Resolution resolved;
    private Escalation escalatedToMissed;
    private List<ProtocolResolution> byProtocol;

    @Data
    @Builder
    public static class Resolution {
        private long count;
        private double percentage;
        private Double avgDaysToResolve;
    }

    @Data
    @Builder
    public static class Escalation {
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    public static class ProtocolResolution {
        private String protocolDefinitionId;
        private String protocolCanonical;
        private long totalOverdue;
        private long resolvedCount;
        private double resolutionRate;
        private long escalatedCount;
    }
}
