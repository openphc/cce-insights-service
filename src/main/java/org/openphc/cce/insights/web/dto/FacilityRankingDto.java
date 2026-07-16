package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FacilityRankingDto {
    private int rank;
    private String facilityId;
    private String facilityName;
    /** Facility's district (may be empty). Enables the district filter in the compliance drill-down. */
    private String district;
    private long totalEnrollments;
    private long compliantPatients;
    private long nonCompliantPatients;
    private double complianceRate;
    private long activeDeviations;
    private long totalEvents;
    private long patientsFromHIE;
}
