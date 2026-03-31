package org.openphc.cce.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestionFunnelDto {
    private long totalReceived;
    private long accepted;
    private long rejected;
    private long duplicate;
    private double acceptanceRate;
    private double rejectionRate;
    private double duplicateRate;
    private List<StatusBreakdown> breakdown;
    private List<TrendPoint> trends;

    @Data
    @Builder
    public static class StatusBreakdown {
        private String status;
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    public static class TrendPoint {
        private String period;
        private Map<String, Long> byStatus;
        private long total;
    }
}
