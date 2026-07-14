package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/** Active-facility summary tile — derived live from mv_event_volume_hourly. */
@Data
@Builder
public class FacilityActivitySummaryDto {
    private long totalInScope;
    private long activeFacilities;
    private long inactiveFacilities;
    private double activeFacilityRate;
}
