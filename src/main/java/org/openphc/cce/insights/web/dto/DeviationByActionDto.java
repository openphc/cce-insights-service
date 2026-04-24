package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DeviationByActionDto {
    private String actionId;
    private UUID protocolDefinitionId;
    private String protocolCanonical;
    private long totalDeviations;
    private long overdueCount;
    private long missedCount;
    private long orderViolationCount;
    private long affectedPatients;
}
