package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeviationTrendDto {
    private String interval;
    private List<TrendPoint> trends;

    @Data
    @Builder
    public static class TrendPoint {
        private String period;
        private long overdue;
        private long missed;
        private long total;
    }
}
