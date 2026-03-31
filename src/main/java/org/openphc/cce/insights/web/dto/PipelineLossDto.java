package org.openphc.cce.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineLossDto {
    private long totalAcceptedByCollector;
    private long totalInComplianceEventLog;
    private long lostEvents;
    private double lossRate;
    private List<SourceLoss> bySource;

    @Data
    @Builder
    public static class SourceLoss {
        private String source;
        private long lostEvents;
    }
}
