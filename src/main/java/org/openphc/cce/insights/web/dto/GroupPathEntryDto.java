package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * One group instance around a step instance, such as "Outpatient Visit #2". A step in a nested group
 * has one entry per level, from the top-level group in.
 */
@Data
@Builder
public class GroupPathEntryDto {
    private String groupActionId;
    private String groupName;          // the group's title in the protocol
    private String groupInstanceId;
    private Integer number;            // its number among its group's instances in the same parent instance,
                                       // from 1 in opening order; null when it is the only one
    private OffsetDateTime openedAt;
}
