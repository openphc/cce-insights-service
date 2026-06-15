package org.openphc.cce.insights.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openphc.cce.insights.domain.enums.CompletionStatus;
import org.openphc.cce.insights.domain.enums.StepState;

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
    private StepState state;
    private OffsetDateTime dueDate;
    private OffsetDateTime overdueDate;
    private OffsetDateTime missedDate;
    private OffsetDateTime completedAt;
    private String completedBySource;
    private CompletionStatus completionStatus;
    private UUID completedByEventId;
    private String requiredBehavior;
}
