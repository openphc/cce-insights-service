package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActionOrderEntryDto {
    private String actionId;
    private String parentActionId;
    private String type;
    private String title;
    /** true for a step group (groupingBehavior logical-group). A group has no step data of its own. */
    private boolean group;
}
