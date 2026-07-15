package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/**
 * One facility in the Active/Inactive drill-down list (RI-29). Backs the modal opened from the
 * Active/Inactive facility cards on the Dashboard and Facilities pages.
 */
@Data
@Builder
public class FacilityActivityItemDto {
    private String facilityId;
    private String facilityName;
    /** May be empty when the source has no district for the facility. */
    private String district;
    /** Most recent day (yyyy-MM-dd) the facility transmitted an accepted event, up to the period end
     *  (includes activity before the window, so inactive-in-period facilities still show a last-seen
     *  date); null only if the facility was never active up to the period end. */
    private String lastActivity;
    /** true = transmitted ≥1 accepted event in the selected range; false = inactive. */
    private boolean active;
}
