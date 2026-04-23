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
        private String stepName;
        private String status;       // COMPLETED, PENDING, NOT_STARTED, OVERDUE, MISSED, SKIPPED
        private int completionCount;
        private String effectiveDateTime;
        private String completionStatus;
        private String source;
    }

    @Data
    @Builder
    public static class TimelineEvent {
        private OffsetDateTime timestamp;
        private String type;
        private String description;
        private String actionId;
        private String stepName;
        private String state;
        private String completionStatus;
        private String source;
        private Integer daysOverdue;
        private String effectiveDateTime;
    }
}
