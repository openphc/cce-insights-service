package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProcessingQualityDto {
    private long totalEvents;
    private Map<String, StatusDetail> overall;
    private List<SourceQuality> bySource;

    @Data
    @Builder
    public static class StatusDetail {
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    public static class SourceQuality {
        private String source;
        private long totalEvents;
        private Map<String, StatusDetail> breakdown;
    }
}
