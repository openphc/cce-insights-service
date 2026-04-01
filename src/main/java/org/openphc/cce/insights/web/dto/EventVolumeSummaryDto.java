package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class EventVolumeSummaryDto {
    private long totalEvents;
    private Map<String, StatusCount> processingStatusBreakdown;
    private List<ResourceTypeCountDto> byResourceType;
    private List<FacilityCount> byFacility;
    private List<SourceCount> bySource;

    @Data
    @Builder
    public static class StatusCount {
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    public static class FacilityCount {
        private String facilityId;
        private long count;
    }

    @Data
    @Builder
    public static class SourceCount {
        private String source;
        private long count;
    }
}
