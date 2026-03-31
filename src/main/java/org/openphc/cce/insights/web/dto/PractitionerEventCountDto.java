package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PractitionerEventCountDto {
    private String practitionerRef;
    private String practitionerDisplay;
    private String facilityId;
    private long totalEvents;
    private List<ResourceTypeCountDto> byResourceType;
}
