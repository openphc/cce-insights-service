package org.openphc.cce.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceComparisonDto {
    private String sourceA;
    private String sourceB;
    private long matchWindowSeconds;
    private SourceSummary sourceASummary;
    private SourceBSummary sourceBSummary;
    private OverlapSummary overlap;
    private List<OverlapSample> samples;

    @Data
    @Builder
    public static class SourceSummary {
        private String source;
        private long totalEvents;
        private long uniqueEvents;
        private long overlappingEvents;
        private double overlapPercentage;
        private List<ResourceTypeCount> uniqueByResourceType;
    }

    @Data
    @Builder
    public static class SourceBSummary {
        private String source;
        private long totalEvents;
        private long uniqueEvents;
        private long overlappingEvents;
        private double overlapPercentage;
        private List<ResourceTypeCount> uniqueByResourceType;
    }

    @Data
    @Builder
    public static class OverlapSummary {
        private long totalOverlappingEvents;
        private List<ResourceTypeCount> byResourceType;
    }

    @Data
    @Builder
    public static class ResourceTypeCount {
        private String resourceType;
        private long count;
    }

    @Data
    @Builder
    public static class OverlapSample {
        private UUID eventAId;
        private UUID eventBId;
        private String subject;
        private String resourceType;
        private OffsetDateTime eventTimeA;
        private OffsetDateTime eventTimeB;
        private double timeDiffSeconds;
    }
}
