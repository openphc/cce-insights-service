package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/** Active-facility summary tile — a facility is active if it has ≥1 accepted inbound event
 *  in the period (see DailyKpiRepositoryImpl / data dictionary §3.13a). */
@Data
@Builder
public class FacilityActivitySummaryDto {
    private long totalInScope;
    private long activeFacilities;
    private long inactiveFacilities;
    private double activeFacilityRate;
}
