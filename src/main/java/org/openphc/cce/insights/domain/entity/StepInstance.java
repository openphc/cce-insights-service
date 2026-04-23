package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.openphc.cce.insights.domain.enums.CompletionStatus;
import org.openphc.cce.insights.domain.enums.StepState;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "step_instance")
@Immutable
@Getter
public class StepInstance {

    @Id
    private UUID id;

    @Column(name = "protocol_instance_id")
    private UUID protocolInstanceId;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "repeat_index")
    private Integer repeatIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private StepState state;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "overdue_date")
    private OffsetDateTime overdueDate;

    @Column(name = "missed_date")
    private OffsetDateTime missedDate;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by_source")
    private String completedBySource;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_status")
    private CompletionStatus completionStatus;

    @Column(name = "matched_event_id")
    private UUID matchedEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_instance_id", insertable = false, updatable = false)
    private ProtocolInstance protocolInstance;

    protected StepInstance() {
    }
}
