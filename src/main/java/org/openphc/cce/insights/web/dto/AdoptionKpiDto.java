package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/** Maps mv_daily_adoption_kpis — one row per facility. */
@Data
@Builder
public class AdoptionKpiDto {
    private String facilityId;
    private String facilityName;
    /** Validated baseline from facility (set by programme staff). */
    private long expectedVisitsPerDay;
    /** Average daily distinct reporters over the selected period (or today when no range).
     *  Float so small averages (e.g. 15 visits over 90 days = 0.17) are not rounded to 0. */
    private double actualVisitsPerDay;
    /** actual / expected × 100 (period total when a date range is supplied). */
    private double adoptionRate;
    /** Average daily expected − actual; positive = under-reporting. Float for the same
     *  reason as {@link #actualVisitsPerDay}. */
    private double reportingGapPerDay;
}
