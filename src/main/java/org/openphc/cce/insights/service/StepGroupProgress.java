package org.openphc.cce.insights.service;

import org.openphc.cce.insights.domain.entity.StepGroupInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.SlaStatus;
import org.openphc.cce.insights.domain.enums.StepStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The step groups of one enrollment: each group instance, the step instances in it, and how far
 * along it is.
 *
 * A step group is a set of steps that belong together, such as one visit. Each time it opens for
 * the patient, the Matcher writes a group instance. A group that repeats has one per cycle. A
 * group inside a group has one per instance of its parent.
 */
final class StepGroupProgress {

    private static final Comparator<StepGroupInstance> IN_OPENING_ORDER =
            Comparator.comparing(StepGroupInstance::getOpenedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(StepGroupInstance::getCycleIndex);

    private final Map<UUID, StepGroupInstance> groupInstancesById;
    private final Map<String, List<StepGroupInstance>> groupInstancesByAction;
    private final Map<UUID, List<StepGroupInstance>> groupInstancesByParent;
    private final Map<UUID, List<StepInstance>> stepInstancesByGroupInstance;

    StepGroupProgress(List<StepGroupInstance> groupInstances, List<StepInstance> stepInstances) {
        List<StepGroupInstance> inOpeningOrder = groupInstances.stream().sorted(IN_OPENING_ORDER).toList();
        this.groupInstancesById = inOpeningOrder.stream()
                .collect(Collectors.toMap(StepGroupInstance::getId, Function.identity(), (a, b) -> a));
        this.groupInstancesByAction = inOpeningOrder.stream()
                .collect(Collectors.groupingBy(StepGroupInstance::getGroupActionId));
        this.groupInstancesByParent = inOpeningOrder.stream()
                .filter(g -> g.getParentGroupInstanceId() != null)
                .collect(Collectors.groupingBy(StepGroupInstance::getParentGroupInstanceId));
        this.stepInstancesByGroupInstance = stepInstances.stream()
                .filter(s -> s.getStepGroupInstanceId() != null)
                .collect(Collectors.groupingBy(StepInstance::getStepGroupInstanceId));
    }

    /** The instances of one group, in the order they opened. Empty if it never opened. */
    List<StepGroupInstance> instancesOf(String groupActionId) {
        return groupInstancesByAction.getOrDefault(groupActionId, List.of());
    }

    /** The group instance a step instance belongs to. Empty for a step in no group. */
    Optional<StepGroupInstance> groupInstanceOf(StepInstance stepInstance) {
        return Optional.ofNullable(stepInstance.getStepGroupInstanceId()).map(groupInstancesById::get);
    }

    /**
     * The group instances around a step instance, from the top-level group in: the one it is in comes
     * last. Empty for a step in no group.
     */
    List<StepGroupInstance> groupInstancesAround(StepInstance stepInstance) {
        List<StepGroupInstance> around = new ArrayList<>();
        StepGroupInstance groupInstance = groupInstanceOf(stepInstance).orElse(null);
        while (groupInstance != null) {
            around.add(0, groupInstance);
            groupInstance = groupInstance.getParentGroupInstanceId() != null
                    ? groupInstancesById.get(groupInstance.getParentGroupInstanceId())
                    : null;
        }
        return around;
    }

    /**
     * A group instance's number among its group's instances in the same parent instance, from 1, in
     * the order they opened: the second visit is 2. Null when it is the only one. A nested group is
     * numbered from 1 again inside each instance of its parent.
     */
    Integer numberOf(StepGroupInstance groupInstance) {
        List<StepGroupInstance> siblings = instancesOf(groupInstance.getGroupActionId()).stream()
                .filter(g -> Objects.equals(g.getParentGroupInstanceId(), groupInstance.getParentGroupInstanceId()))
                .toList();
        if (siblings.size() < 2) return null;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(groupInstance.getId())) return i + 1;
        }
        return null;
    }

    /** The step instances in a group instance, including those in the groups nested inside it. */
    List<StepInstance> stepInstancesIn(StepGroupInstance groupInstance) {
        List<StepInstance> result = new ArrayList<>(
                stepInstancesByGroupInstance.getOrDefault(groupInstance.getId(), List.of()));
        for (StepGroupInstance nested : groupInstancesByParent.getOrDefault(groupInstance.getId(), List.of())) {
            result.addAll(stepInstancesIn(nested));
        }
        return result;
    }

    /**
     * One status per group instance, on the same scale as a step's
     * ({@link StepInstance#displayStatus()}):
     * <ul>
     *   <li>COMPLETED: the Matcher recorded it as complete. Or every step in it so far is done, so
     *       nothing in it is still awaited.</li>
     *   <li>OVERDUE or MISSED: the verdict on the group's own deadline.</li>
     *   <li>NOT_STARTED: it is open, and some step in it is still awaited.</li>
     * </ul>
     */
    String displayStatus(StepGroupInstance groupInstance) {
        if (isDone(groupInstance)) return StepStatus.COMPLETED.name();
        SlaStatus sla = groupInstance.getSlaStatus();
        if (sla == SlaStatus.OVERDUE || sla == SlaStatus.MISSED) return sla.name();
        return StepStatus.NOT_STARTED.name();
    }

    boolean isDone(StepGroupInstance groupInstance) {
        if (groupInstance.getCompletedAt() != null) return true;
        List<StepInstance> stepInstances = stepInstancesIn(groupInstance);
        return !stepInstances.isEmpty() && stepInstances.stream().allMatch(StepInstance::isCompleted);
    }

    /**
     * When the group instance was done: the Matcher's completion time, else its last step's.
     * Null while it isn't done.
     */
    OffsetDateTime doneAt(StepGroupInstance groupInstance) {
        if (groupInstance.getCompletedAt() != null) return groupInstance.getCompletedAt();
        if (!isDone(groupInstance)) return null;
        return stepInstancesIn(groupInstance).stream()
                .map(StepInstance::getCompletedAt)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
