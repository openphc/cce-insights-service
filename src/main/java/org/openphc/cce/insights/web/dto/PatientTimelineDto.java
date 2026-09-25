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
    }
}
