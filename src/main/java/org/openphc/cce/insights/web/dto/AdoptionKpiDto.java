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
    /** Average daily distinct reporters, rounded UP to a whole number so a sparse
     *  non-zero average (e.g. 1 visit over 90 days = 0.011/day) still shows as ≥ 1. */
    private long actualVisitsPerDay;
    /** actual / expected × 100 (period total when a date range is supplied). */
    private double adoptionRate;
    /** {@code expectedVisitsPerDay − actualVisitsPerDay}; positive = under-reporting.
     *  Always a whole number — derived from the displayed `actualVisitsPerDay` so the
     *  three columns are guaranteed to reconcile. */
    private long reportingGapPerDay;
}
