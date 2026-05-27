package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PractitionerRankingDto {
    private int rank;
    private String practitionerRef;
    private String practitionerName;
    private String facilityId;
    private long totalPatients;
    private double complianceRate;
    private long totalSteps;
    private long completedSteps;
    private long activeDeviations;
    private long totalEvents;
}
