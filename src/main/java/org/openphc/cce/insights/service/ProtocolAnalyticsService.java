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
    private final ComplianceEventLogRepository complianceEventLogRepository;
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
                        .build());
                JsonNode subActions = action.get("action");
                if (subActions != null && subActions.isArray()) {
                    collectActionEntriesRecursive(subActions, actionId, entries);
                }
            }
        }
    }

    @Cacheable(value = "analytics",
            key = "'step-analytics-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public StepAnalyticsDto getStepAnalytics(UUID protocolDefinitionId, String facilityId,
                                              OffsetDateTime startDate, OffsetDateTime endDate) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        // Resolve requiredBehavior per actionId from PlanDefinition
        Map<String, String> requiredBehaviorMap = resolveRequiredBehaviorMap(pd);

        // Date range narrows the cohort to enrollments enrolled in the period; the
        // step-state distribution is therefore reported relative to that cohort.
        // (Step states are still "current state at observation time" — we don't replay
        //  state history, so transitions that happened in the period aren't isolated.)
        List<Object[]> rows = (facilityId != null && !facilityId.isEmpty())
                ? stepInstanceRepository.findStepAnalyticsByFacility(
                        protocolDefinitionId, facilityId, startDate, endDate)
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
                            .early(((Number) row[3]).longValue())
                            .onTime(((Number) row[4]).longValue())
                            .late(((Number) row[5]).longValue())
                            .build())
                    .overdueCount(((Number) row[6]).longValue())
                    .missedCount(((Number) row[7]).longValue())
                    .skippedCount(((Number) row[8]).longValue())
                    .pendingCount(((Number) row[9]).longValue())
                    .avgDaysToComplete(row[10] != null ? ((Number) row[10]).doubleValue() : null)
                    .medianDaysToComplete(row[11] != null ? ((Number) row[11]).doubleValue() : null)
                    .requiredBehavior(requiredBehaviorMap.get(actionId))
                    .build();
        }).collect(Collectors.toList());

        return StepAnalyticsDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .steps(steps)
                .build();
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

    @Cacheable(value = "analytics",
            key = "'funnel-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public CompletionFunnelDto getCompletionFunnel(UUID protocolDefinitionId, String facilityId,
                                                    OffsetDateTime startDate, OffsetDateTime endDate) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        // Both the funnel rows and totalEnrollments are scoped to enrollments in the
        // selected date range and (optionally) facility.
        List<Object[]> rows = stepInstanceRepository.findCompletionFunnel(
                protocolDefinitionId, facilityId, startDate, endDate);
        long totalEnrollments;
        if (facilityId != null && !facilityId.isEmpty()) {
            // Approximate: count distinct patients in scope by replaying the same join
            // logic — defer to a dedicated query if accuracy becomes critical.
            totalEnrollments = rows.stream()
                    .mapToLong(r -> ((Number) r[1]).longValue())
                    .max()
                    .orElse(0L);
        } else if (startDate != null || endDate != null) {
            totalEnrollments = protocolInstanceRepository.findByProtocolDefinitionIdAndEnrolledBetween(
                    protocolDefinitionId, startDate, endDate).size();
        } else {
            totalEnrollments = protocolInstanceRepository.findByProtocolDefinitionId(
                    protocolDefinitionId).size();
        }

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

    @Cacheable(value = "analytics",
            key = "'outcome-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public OutcomeDistributionDto getOutcomeDistribution(UUID protocolDefinitionId, String facilityId,
                                                          OffsetDateTime startDate, OffsetDateTime endDate) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        // Narrow to enrollments in the selected date range; if no range we still see all-time.
        // When a facility is selected, further restrict to patients tied to that facility
        // via mv_patient_facility_latest (consistent with patient cohort scoping elsewhere).
        List<org.openphc.cce.insights.domain.entity.ProtocolInstance> instances =
                (startDate != null || endDate != null)
                        ? protocolInstanceRepository.findByProtocolDefinitionIdAndEnrolledBetween(
                                protocolDefinitionId, startDate, endDate)
                        : protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        if (facilityId != null && !facilityId.isEmpty()) {
            java.util.Set<String> patientsAtFacility = complianceEventLogRepository
                    .findPatientsByFacility(facilityId)
                    .stream().map(r -> (String) r[1])
                    .collect(Collectors.toSet());
            instances = instances.stream()
                    .filter(pi -> patientsAtFacility.contains(pi.getPatientId()))
                    .collect(Collectors.toList());
        }
        List<Object[]> rows = instances.stream()
                .collect(Collectors.groupingBy(
                        pi -> pi.getStatus().name(),
                        Collectors.counting()))
                .entrySet().stream()
                .map(e -> new Object[]{e.getKey(), e.getValue()})
                .collect(Collectors.toList());
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

    @Cacheable(value = "analytics",
            key = "'enrollment-' + #protocolDefinitionId + '-' + #interval + '-' + (#facilityId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public EnrollmentTrendDto getEnrollmentTrends(UUID protocolDefinitionId, String interval,
                                                   String facilityId,
                                                   OffsetDateTime startDate, OffsetDateTime endDate) {
        protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        String dbInterval = DateUtil.mapInterval(interval);
        List<Object[]> rows = (facilityId != null && !facilityId.isEmpty())
                ? protocolInstanceRepository.findEnrollmentTrendsByFacility(
                        protocolDefinitionId, facilityId, dbInterval, startDate, endDate)
                : protocolInstanceRepository.findEnrollmentTrends(
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
