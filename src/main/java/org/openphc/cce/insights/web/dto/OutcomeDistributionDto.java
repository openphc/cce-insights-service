package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class OutcomeDistributionDto {
    private UUID protocolDefinitionId;
    private String protocolCanonical;
    private long totalInstances;
    private Map<String, StatusCount> distribution;

    @Data
    @Builder
    public static class StatusCount {
        private long count;
        private double percentage;
    }
}
