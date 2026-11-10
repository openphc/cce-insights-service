package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StepAnalyticsDto {
    private UUID protocolDefinitionId;
    private String protocolCanonical;
    private List<StepMetric> steps;
    /** One per step group, such as a visit, in protocol order. Empty for a protocol without groups. */
    private List<GroupMetric> groups;

    /**
     * A step's counts. totalInstances, completedCount and the SLA counts are distinct patients: a
     * patient counts once, however many visits they had. stepInstanceCount and
     * completedStepInstanceCount count step instances, one per visit for a step in a group that
     * repeats.
     */
    @Data
    @Builder
    public static class StepMetric {
        private String actionId;
        private long totalInstances;     // patients with a step instance of it
        private long completedCount;     // patients who completed it at least once
        private double completionRate;
        private TimelinessDistribution timelinessDistribution;
        private long overdueCount;       // sla_status = OVERDUE (includes steps completed late)
        private long missedCount;        // sla_status = MISSED (includes steps completed after write-off)
        private long notStartedCount;    // step_status = NOT_STARTED
        private long slaUnjudgedCount;   // sla_status not yet set
        private Double avgDaysToComplete;
        private Double medianDaysToComplete;
        private String requiredBehavior;
        private long stepInstanceCount;           // its step instances, one per visit in a repeating group
        private long completedStepInstanceCount;  // of those, the completed ones
    }

    /**
     * A step group's counts, such as for a visit. An instance is one occurrence of the group, such as
     * one visit. It is completed when the Matcher recorded it as complete, or when every step in it so
     * far is done, nested groups' steps included, as on the patient's journey.
     */
    @Data
    @Builder
    public static class GroupMetric {
        private String groupActionId;
        private String title;
        private long patientCount;            // patients with at least one instance of it
        private long instanceCount;           // its instances, such as visits
        private long completedInstanceCount;
        private double completionRate;        // completedInstanceCount / instanceCount, 0-1
    }

    /** Completed steps by SLA verdict. 2.0.0 has no early/on-time split — both are MET. */
    @Data
    @Builder
    public static class TimelinessDistribution {
        private long completedOnTime;    // COMPLETED + MET
        private long completedLate;      // COMPLETED + OVERDUE | MISSED
    }
}
