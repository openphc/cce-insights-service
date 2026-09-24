package org.openphc.cce.insights.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openphc.cce.insights.domain.enums.SlaStatus;
import org.openphc.cce.insights.domain.enums.StepStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepInstance {

    private UUID id;
    private UUID protocolInstanceId;
    private String actionId;
    private Integer repeatIndex;
    private StepStatus stepStatus;
    /** null = not yet judged ('' in ClickHouse). */
    private SlaStatus slaStatus;
    private OffsetDateTime dueDate;
    private OffsetDateTime completedAt;
    private String completedBySource;
    private UUID matchedEventId;
    private String requiredBehavior;

    public boolean isCompleted() {
        return stepStatus == StepStatus.COMPLETED;
    }

    /**
     * Single display status for the patient views, which show one badge per step:
     * COMPLETED, else the SLA verdict of the outstanding step (OVERDUE | MISSED), else NOT_STARTED.
     * 1.x PENDING and DUE both land on NOT_STARTED; SKIPPED no longer exists.
     */
    public String displayStatus() {
        if (isCompleted()) return StepStatus.COMPLETED.name();
        if (slaStatus == SlaStatus.OVERDUE || slaStatus == SlaStatus.MISSED) return slaStatus.name();
        return StepStatus.NOT_STARTED.name();
    }
}
