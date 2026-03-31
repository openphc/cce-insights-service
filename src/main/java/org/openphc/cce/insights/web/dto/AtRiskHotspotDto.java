package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AtRiskHotspotDto {
    private String facilityId;
    private long totalPatients;
    private CategoryCount onTrack;
    private CategoryCount atRisk;
    private CategoryCount nonCompliant;

    @Data
    @Builder
    public static class CategoryCount {
        private long count;
        private double percentage;
    }
}
