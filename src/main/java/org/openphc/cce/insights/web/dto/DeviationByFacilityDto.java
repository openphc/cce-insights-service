package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviationByFacilityDto {
    private String facilityId;
    private long overdueCount;
    private long missedCount;
    private long orderViolationCount;
    private long totalDeviations;
}
