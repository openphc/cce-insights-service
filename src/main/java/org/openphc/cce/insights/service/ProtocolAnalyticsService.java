package org.openphc.cce.insights.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProtocolAnalyticsService {

    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "analytics", key = "'action-order-' + #protocolDefinitionId")
    public List<ActionOrderEntryDto> getActionOrder(UUID protocolDefinitionId) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<ActionOrderEntryDto> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(pd.getDefinition());
            JsonNode actions = root.get("action");
            if (actions != null && actions.isArray()) {
                collectActionEntriesRecursive(actions, null, entries);
            }
        } catch (Exception e) {
            // fallback: return empty list, UI will use default order
        }
        return entries;
    }

    private void collectActionEntriesRecursive(JsonNode actions, String parentActionId, List<ActionOrderEntryDto> entries) {
        for (JsonNode action : actions) {
            JsonNode idNode = action.get("id");
            if (idNode != null && !idNode.isNull()) {
                String actionId = idNode.asText();
                String type = null;
                JsonNode typeNode = action.path("type").path("coding");
                if (typeNode.isArray() && typeNode.size() > 0) {
                    type = typeNode.get(0).path("code").asText(null);
                }
                String title = action.has("title") ? action.get("title").asText(null) : null;
                entries.add(ActionOrderEntryDto.builder()
                        .actionId(actionId)
                        .parentActionId(parentActionId)
                        .type(type)
                        .title(title)
                        .group(PlanDefinitionActions.isStepGroup(action))
                        .build());
                JsonNode subActions = action.get("action");
                if (subActions != null && subActions.isArray()) {
                    collectActionEntriesRecursive(subActions, actionId, entries);
                }
            }
        }
    }

    /**
     * Per-step analytics of one protocol for the patients enrolled within [startDate, endDate]
     * ({@code enrolled_at}, the clinical time of the enrolling event); a null bound is open.
     */
    @Cacheable(value = "analytics",
            key = "'step-analytics-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all') + '-' + #startDate + '-' + #endDate")
    public StepAnalyticsDto getStepAnalytics(UUID protocolDefinitionId, String facilityId,
                                             OffsetDateTime startDate, OffsetDateTime endDate) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        // Resolve requiredBehavior per actionId from PlanDefinition
        Map<String, String> requiredBehaviorMap = resolveRequiredBehaviorMap(pd);

        List<Object[]> rows = (facilityId != null && !facilityId.isEmpty())
                ? stepInstanceRepository.findStepAnalyticsByFacility(protocolDefinitionId, facilityId, startDate, endDate)
                : stepInstanceRepository.findStepAnalytics(protocolDefinitionId, startDate, endDate);

        List<StepAnalyticsDto.StepMetric> steps = rows.stream().map(row -> {
            String actionId = (String) row[0];
            long totalInst = ((Number) row[1]).longValue();
            long completedCount = ((Number) row[2]).longValue();
            double completionRate = totalInst > 0 ? Math.round((double) completedCount / totalInst * 100.0) / 100.0 : 0;

            return StepAnalyticsDto.StepMetric.builder()
                    .actionId(actionId)
                    .totalInstances(totalInst)
                    .completedCount(completedCount)
                    .completionRate(completionRate)
                    .timelinessDistribution(StepAnalyticsDto.TimelinessDistribution.builder()
                            .completedOnTime(((Number) row[3]).longValue())
                            .completedLate(((Number) row[4]).longValue())
                            .build())
                    .overdueCount(((Number) row[5]).longValue())
                    .missedCount(((Number) row[6]).longValue())
                    .notStartedCount(((Number) row[7]).longValue())
                    .slaUnjudgedCount(((Number) row[8]).longValue())
                    .avgDaysToComplete(row[9] != null ? ((Number) row[9]).doubleValue() : null)
                    .medianDaysToComplete(row[10] != null ? ((Number) row[10]).doubleValue() : null)
                    .requiredBehavior(requiredBehaviorMap.get(actionId))
                    .stepInstanceCount(row.length > 11 && row[11] != null ? ((Number) row[11]).longValue() : totalInst)
                    .completedStepInstanceCount(row.length > 12 && row[12] != null ? ((Number) row[12]).longValue() : completedCount)
                    .build();
        }).collect(Collectors.toList());

        return StepAnalyticsDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .steps(steps)
                .groups(groupMetrics(pd, facilityId, startDate, endDate))
                .build();
    }

    /**
     * One metric per step group of the protocol, in protocol order, over the same enrollments as the
     * step metrics. A group instance counts as completed as it does on the patient's journey
     * ({@link StepGroupProgress#isDone}): the Matcher recorded it, or every step in it so far is done,
     * nested groups' steps included.
     */
    private List<StepAnalyticsDto.GroupMetric> groupMetrics(ProtocolDefinition pd, String facilityId,
                                                            OffsetDateTime startDate, OffsetDateTime endDate) {
        Map<String, String> groupTitles = groupTitlesInOrder(pd);
        if (groupTitles.isEmpty()) return List.of();

        List<Object[]> rows = stepInstanceRepository.findGroupInstanceProgress(
                pd.getId(), facilityId, startDate, endDate);
        Map<UUID, Object[]> rowById = new HashMap<>();
        Map<UUID, List<UUID>> childrenByParent = new HashMap<>();
        for (Object[] row : rows) {
            rowById.put((UUID) row[0], row);
            if (row[1] != null) childrenByParent.computeIfAbsent((UUID) row[1], k -> new ArrayList<>()).add((UUID) row[0]);
        }

        Map<String, Set<String>> patients = new HashMap<>();
        Map<String, long[]> counts = new HashMap<>(); // group action id -> {instances, completed}
        for (Object[] row : rows) {
            String groupActionId = (String) row[2];
            long[] steps = stepsWithNested((UUID) row[0], rowById, childrenByParent);
            boolean done = (Boolean) row[4] || (steps[0] > 0 && steps[1] == steps[0]);
            patients.computeIfAbsent(groupActionId, k -> new HashSet<>()).add((String) row[3]);
            long[] c = counts.computeIfAbsent(groupActionId, k -> new long[2]);
            c[0]++;
            if (done) c[1]++;
        }

        List<StepAnalyticsDto.GroupMetric> metrics = new ArrayList<>();
        groupTitles.forEach((groupActionId, title) -> {
            long[] c = counts.getOrDefault(groupActionId, new long[2]);
            metrics.add(StepAnalyticsDto.GroupMetric.builder()
                    .groupActionId(groupActionId)
                    .title(title)
                    .patientCount(patients.getOrDefault(groupActionId, Set.of()).size())
                    .instanceCount(c[0])
                    .completedInstanceCount(c[1])
                    .completionRate(c[0] > 0 ? Math.round((double) c[1] / c[0] * 100.0) / 100.0 : 0)
                    .build());
        });
        return metrics;
    }

    /** {steps, done} of a group instance, the groups nested inside it included. */
    private static long[] stepsWithNested(UUID groupInstanceId, Map<UUID, Object[]> rowById,
                                          Map<UUID, List<UUID>> childrenByParent) {
        Object[] row = rowById.get(groupInstanceId);
        long[] total = {(Long) row[5], (Long) row[6]};
        for (UUID child : childrenByParent.getOrDefault(groupInstanceId, List.of())) {
            long[] nested = stepsWithNested(child, rowById, childrenByParent);
            total[0] += nested[0];
            total[1] += nested[1];
        }
        return total;
    }

    /** The protocol's step groups, id to title, in protocol order. */
    private Map<String, String> groupTitlesInOrder(ProtocolDefinition pd) {
        Map<String, String> titles = new LinkedHashMap<>();
        try {
            collectGroupTitles(objectMapper.readTree(pd.getDefinition()).get("action"), titles);
        } catch (Exception e) {
            // fallback: no groups
        }
        return titles;
    }

    private static void collectGroupTitles(JsonNode actions, Map<String, String> titles) {
        if (actions == null || !actions.isArray()) return;
        for (JsonNode action : actions) {
            if (PlanDefinitionActions.isStepGroup(action) && action.hasNonNull("id")) {
                String id = action.get("id").asText();
                titles.put(id, action.hasNonNull("title") ? action.get("title").asText() : id);
            }
            collectGroupTitles(action.get("action"), titles);
        }
    }

    private Map<String, String> resolveRequiredBehaviorMap(ProtocolDefinition pd) {
        Map<String, String> map = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(pd.getDefinition());
            JsonNode actions = root.get("action");
            if (actions != null && actions.isArray()) {
                collectRequiredBehavior(actions, map);
            }
        } catch (Exception e) {
            // fallback: empty map
        }
        return map;
    }

    private void collectRequiredBehavior(JsonNode actions, Map<String, String> map) {
        for (JsonNode action : actions) {
            JsonNode idNode = action.get("id");
            JsonNode rbNode = action.get("requiredBehavior");
            if (idNode != null && !idNode.isNull() && rbNode != null && !rbNode.isNull()) {
                map.put(idNode.asText(), rbNode.asText());
            }
            JsonNode subActions = action.get("action");
            if (subActions != null && subActions.isArray()) {
                collectRequiredBehavior(subActions, map);
            }
        }
    }

    /** Step completion funnel of one protocol for the patients enrolled within [startDate, endDate]. */
    @Cacheable(value = "analytics", key = "'funnel-' + #protocolDefinitionId + '-' + #startDate + '-' + #endDate")
    public CompletionFunnelDto getCompletionFunnel(UUID protocolDefinitionId,
                                                   OffsetDateTime startDate, OffsetDateTime endDate) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<Object[]> rows = stepInstanceRepository.findCompletionFunnel(protocolDefinitionId, startDate, endDate);
        long totalEnrollments = protocolInstanceRepository
                .countByProtocolDefinitionIdGroupByStatus(protocolDefinitionId, startDate, endDate)
                .stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        List<CompletionFunnelDto.FunnelStep> funnel = new ArrayList<>();
        int order = 1;
        for (Object[] row : rows) {
            long reached = ((Number) row[1]).longValue();
            long completed = ((Number) row[2]).longValue();
            double completionRate = reached > 0 ? Math.round((double) completed / reached * 100.0) / 100.0 : 0;
            double dropOff = reached > 0 ? Math.round((1.0 - (double) completed / reached) * 100.0) / 100.0 : 0;

            funnel.add(CompletionFunnelDto.FunnelStep.builder()
                    .actionId((String) row[0])
                    .stepOrder(order++)
                    .reachedCount(reached)
                    .completedCount(completed)
                    .completionRate(completionRate)
                    .dropOffRate(dropOff)
                    .build());
        }

        return CompletionFunnelDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalEnrollments(totalEnrollments)
                .funnel(funnel)
                .build();
    }

    /** Outcome (status) distribution of one protocol's enrolments within [startDate, endDate]. */
    @Cacheable(value = "analytics", key = "'outcome-' + #protocolDefinitionId + '-' + #startDate + '-' + #endDate")
    public OutcomeDistributionDto getOutcomeDistribution(UUID protocolDefinitionId,
                                                         OffsetDateTime startDate, OffsetDateTime endDate) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<Object[]> rows = protocolInstanceRepository.countByProtocolDefinitionIdGroupByStatus(
                protocolDefinitionId, startDate, endDate);
        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        Map<String, OutcomeDistributionDto.StatusCount> distribution = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String status = row[0].toString().toLowerCase();
            long count = ((Number) row[1]).longValue();
            double pct = total > 0 ? Math.round((double) count / total * 1000.0) / 10.0 : 0;
            distribution.put(status, OutcomeDistributionDto.StatusCount.builder()
                    .count(count).percentage(pct).build());
        }

        return OutcomeDistributionDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalInstances(total)
                .distribution(distribution)
                .build();
    }

    @Cacheable(value = "analytics", key = "'enrollment-' + #protocolDefinitionId + '-' + #interval + '-' + #startDate + '-' + #endDate")
    public EnrollmentTrendDto getEnrollmentTrends(UUID protocolDefinitionId, String interval,
                                                   OffsetDateTime startDate, OffsetDateTime endDate) {
        protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        String dbInterval = DateUtil.mapInterval(interval);
        List<Object[]> rows = protocolInstanceRepository.findEnrollmentTrends(
                protocolDefinitionId, dbInterval, startDate, endDate);

        List<EnrollmentTrendDto.TrendPoint> trends = rows.stream().map(row ->
                EnrollmentTrendDto.TrendPoint.builder()
                        .period(DateUtil.extractDate(row[0]))
                        .enrollments(((Number) row[1]).longValue())
                        .build()
        ).collect(Collectors.toList());

        return EnrollmentTrendDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .interval(interval != null ? interval : "weekly")
                .trends(trends)
                .build();
    }
}
