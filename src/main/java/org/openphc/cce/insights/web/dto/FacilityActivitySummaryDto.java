package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/** Maps mv_daily_facility_activity_summary — single row per refresh cycle. */
@Data
@Builder
public class FacilityActivitySummaryDto {
    private long totalInScope;
    private long activeFacilities;
    private long inactiveFacilities;
    private double activeFacilityRate;
}
