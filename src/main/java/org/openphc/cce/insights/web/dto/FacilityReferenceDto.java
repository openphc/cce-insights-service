package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/** Maps facility — the agreed facility list managed by programme staff. */
@Data
@Builder
public class FacilityReferenceDto {
    private String facilityId;
    private String facilityName;
    /** Expected number of unique patients per day (adoption baseline). */
    private long expectedPatientsPerDay;
}
