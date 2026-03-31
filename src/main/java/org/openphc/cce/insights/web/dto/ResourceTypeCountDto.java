package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceTypeCountDto {
    private String resourceType;
    private long count;
    private double percentage;
}
