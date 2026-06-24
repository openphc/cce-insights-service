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
    private long expectedPatientsPerDay;
    /** Unique patients with HIE events today. */
    private long actualPatients;
    /** actual / expected × 100 */
    private double adoptionRate;
    /** expected − actual; positive = under-reporting. */
    private long reportingGap;
}
