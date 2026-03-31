package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class EventVolumeTrendDto {
    private String interval;
    private List<TrendPoint> trends;

    @Data
    @Builder
    public static class TrendPoint {
        private String period;
        private long total;
        private Map<String, Long> byResourceType;
    }
}
