package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SourceSystemCountDto {
    private String source;
    private long totalEvents;
    private List<ResourceTypeCountDto> byResourceType;
}
