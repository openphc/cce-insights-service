package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class IntelligenceSummaryDto {
    private long totalDeviations;
    private Map<String, Long> byType;
    private Map<String, Long> bySeverity;
    private RecentActivity recentActivity;

    @Data
    @Builder
    public static class RecentActivity {
        private long last24Hours;
        private long last7Days;
        private long last30Days;
    }
}
