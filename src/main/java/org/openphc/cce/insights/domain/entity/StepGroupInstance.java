package org.openphc.cce.insights.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openphc.cce.insights.domain.enums.SlaStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One occurrence of a step group for one enrollment (step_group_instances, Matcher V8).
 *
 * A step group is a set of steps that belong together, such as one visit. Each time a patient's
 * group opens, the Matcher writes one row. A group that repeats gets one row per cycle. A step
 * points at its row through {@link StepInstance#getStepGroupInstanceId()}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepGroupInstance {

    private UUID id;
    private UUID protocolInstanceId;
    /** The enclosing group's row, for a group inside a group. Null for a top-level group. */
    private UUID parentGroupInstanceId;
    /** The group's action.id in the protocol. */
    private String groupActionId;
    /** 0 for the first cycle, then 1, 2, … in the order the cycles opened. */
    private int cycleIndex;
    private OffsetDateTime dueDate;
    /** The verdict on the group's own deadline. null = not judged ('' in ClickHouse). */
    private SlaStatus slaStatus;
    /** Clinical time of what opened this occurrence. */
    private OffsetDateTime openedAt;
    /** When the last mandatory step was done. Not written by the Matcher yet. */
    private OffsetDateTime completedAt;
    /** From when nothing more is expected. Not written by the Matcher yet. */
    private OffsetDateTime closedAt;
}
