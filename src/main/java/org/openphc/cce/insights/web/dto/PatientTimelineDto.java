package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class PatientTimelineDto {
    private String patientId;
    private List<ProtocolTimeline> protocols;

    @Data
    @Builder
    public static class ProtocolTimeline {
        private String protocolInstanceId;
        private String protocolCanonical;
        private String status;
        private double complianceRate;
        private List<JourneyStep> journey;
        private List<TimelineEvent> timeline;
    }

    @Data
    @Builder
    public static class JourneyStep {
        private String actionId;
        private String parentActionId;
        private String stepName;
        private String status;       // COMPLETED | OVERDUE | MISSED | NOT_STARTED (StepInstance.displayStatus())
        private String stepStatus;   // NOT_STARTED | COMPLETED; null when no step_instance exists yet
                                     // (for a group: null when it never opened)
        private String slaStatus;    // OVERDUE | MISSED | MET; null = not yet judged
        private int completionCount;
        private String effectiveDateTime;
        private String dueDate;
        private String source;
        private String practitioner;
        private String facilityId;
        private String facilityName;
        private String requiredBehavior; // must, could
        private String description;
        @Builder.Default
        private int depth = 0;
        /**
         * true for a step group, such as a visit. A group row's status is that of its best
         * instance, on the same scale as a step's, and completionCount counts its done instances.
         */
        private boolean group;
        /** A group row's instances, in the order they opened. Null for a step. */
        private List<GroupInstance> groupInstances;
        /**
         * A step row's step instances, each with the group instance it is in, so a client can show
         * one cycle of a group at a time. The row's own fields merge them all. Empty for a step with
         * no step instance yet; null for a group.
         */
        private List<JourneyStepInstance> instances;
    }

    /**
     * One step instance of a journey row: one occurrence of the step, such as the step in one visit.
     */
    @Data
    @Builder
    public static class JourneyStepInstance {
        private String stepInstanceId;
        private String groupInstanceId; // the group instance it is in; null for a step in no group
        private String status;          // COMPLETED | OVERDUE | MISSED | NOT_STARTED (StepInstance.displayStatus())
        private String stepStatus;      // NOT_STARTED | COMPLETED
        private String slaStatus;       // OVERDUE | MISSED | MET; null = not yet judged
        private String effectiveDateTime;
        private String dueDate;
        private String source;
        private String practitioner;
        private String facilityId;
        private String facilityName;
        private String description;     // its deviation, in words; null when it has none
    }

    /**
     * One occurrence of a step group for the patient. A group that repeats has one per cycle.
     */
    @Data
    @Builder
    public static class GroupInstance {
        private String groupInstanceId;
        private String parentGroupInstanceId; // the enclosing group's instance; null for a top-level group
        private int cycleIndex;               // 0 for the first cycle, then 1, 2, …
        private String status;                // COMPLETED | OVERDUE | MISSED | NOT_STARTED (still open)
        private OffsetDateTime openedAt;
        private OffsetDateTime dueDate;
        private OffsetDateTime completedAt;   // when it was done; null while it is open
        private int completedSteps;           // done step instances in it, nested groups included
        private int totalSteps;               // all step instances in it so far, nested groups included
    }

    @Data
    @Builder
    public static class TimelineEvent {
        private OffsetDateTime timestamp;
        private String type;
        private String description;
        private String actionId;
        private String stepName;
        private String state;        // ENROLLED, or the step's display status (see JourneyStep.status)
        private String stepStatus;   // NOT_STARTED | COMPLETED (step events only)
        private String slaStatus;    // OVERDUE | MISSED | MET; null = not yet judged
        private String source;
        private Integer daysOverdue;
        private String effectiveDateTime;
        private String groupActionId;  // the step's group; null for a step in no group
        private Integer cycleIndex;    // which instance of that group (0, 1, …); null for a step in no group
    }
}
