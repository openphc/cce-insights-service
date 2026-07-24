package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZeroMatchEventDto {
    private String resourceType;
    private String code;
    private String category;
    private String facilityId;
    private long count;
    private double percentage;
}
