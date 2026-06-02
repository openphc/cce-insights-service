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
}
