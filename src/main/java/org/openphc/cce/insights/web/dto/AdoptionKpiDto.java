package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/** Maps mv_daily_adoption_kpis — one row per facility. */
@Data
@Builder
public class AdoptionKpiDto {
    private String facilityId;
    private String facilityName;
    /** Facility's district (may be empty when the source has no district). Enables the district filter. */
    private String district;
    /** RI-33: expected patient visits over the WHOLE selected period = baseline/day × days in range
     *  (not a per-day figure). For today's single-day snapshot this equals the daily baseline. */
    private long expectedVisits;
    /** Actual patient visits over the selected period — the sum of daily reporters across the range. */
    private long actualVisits;
    /** actualVisits / expectedVisits × 100 over the period. */
    private double adoptionRate;
    /** {@code expectedVisits − actualVisits} over the period; positive = under-reporting. */
    private long reportingGap;
}
