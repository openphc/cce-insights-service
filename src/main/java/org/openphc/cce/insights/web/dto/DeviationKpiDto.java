package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviationKpiDto {
    private long totalDeviations;
    private long overdueCount;
    private long missedCount;
    private long orderViolationCount;
}
