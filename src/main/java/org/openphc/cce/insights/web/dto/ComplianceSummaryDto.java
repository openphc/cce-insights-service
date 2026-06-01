package org.openphc.cce.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplianceSummaryDto {
    private UUID protocolDefinitionId;
    private String protocolCanonical;
    private long totalEnrollments;
    private long compliantPatients;
    private Map<String, Long> statusBreakdown;
    private double complianceRate;
    private StepMetrics stepMetrics;
    private long deviationCount;
    private Map<String, Long> deviationBreakdown;

    @Data
    @Builder
    public static class StepMetrics {
        private long totalSteps;
        private long completed;
        private long onTime;
        private long late;
        private long early;
        private long overdue;
        private long missed;
        private long due;
        private long pending;
    }
}
