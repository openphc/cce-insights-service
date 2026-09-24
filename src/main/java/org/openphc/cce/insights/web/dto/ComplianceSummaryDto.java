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

    /**
     * Mirrors the step_* columns of mv_daily_compliance_kpis (2.0.0 two-status model).
     * step_status splits the steps into completed + notStarted; sla_status splits them into
     * slaMet + overdue + missed + slaUnjudged. overdue/missed are SLA verdicts, so they include
     * steps that were completed after the threshold; completedOnTime / completedLate read the pair.
     */
    @Data
    @Builder
    public static class StepMetrics {
        private long totalSteps;
        private long completed;          // step_status = COMPLETED
        private long notStarted;         // step_status = NOT_STARTED
        private long slaMet;             // sla_status = MET
        private long overdue;            // sla_status = OVERDUE (completed late, or still outstanding)
        private long missed;             // sla_status = MISSED (completed after write-off, or still outstanding)
        private long slaUnjudged;        // sla_status not yet set (no threshold due yet; optional steps)
        private long completedOnTime;    // COMPLETED + MET
        private long completedLate;      // COMPLETED + OVERDUE | MISSED
    }
}
