package org.openphc.cce.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RejectionAnalyticsDto {
    private long totalRejected;
    private List<ReasonBreakdown> byReason;
    private List<SourceRejection> bySource;

    @Data
    @Builder
    public static class ReasonBreakdown {
        private String reason;
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    public static class SourceRejection {
        private String source;
        private long totalEvents;
        private long rejectedEvents;
        private double rejectionRate;
        private List<ReasonBreakdown> topReasons;
    }
}
