package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.insights.domain.entity.*;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientTimelineService {

    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final DeviationRepository deviationRepository;
    private final EventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    public PatientTimelineDto getTimeline(String patientId) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);

        List<PatientTimelineDto.ProtocolTimeline> protocols = new ArrayList<>();
        for (ProtocolInstance pi : instances) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceIdOrderByDueDateAsc(pi.getId());
            long completed = steps.stream()
                    .filter(s -> s.getState() == StepState.COMPLETED || s.getState() == StepState.SKIPPED)
                    .count();
            double rate = steps.isEmpty() ? 0.0 : (double) completed / steps.size();

            // Resolve ordered action list and titles from protocol definition
            List<String[]> orderedActions = resolveOrderedActions(pi.getProtocolDefinitionId());
            Map<String, String> stepTitles = new LinkedHashMap<>();
            for (String[] pair : orderedActions) {
                stepTitles.put(pair[0], pair[1]);
            }

            // Build effectiveDateTime lookup: stepInstance.matchedEventId → event_log.data.effectiveDateTime
            Map<UUID, String> effectiveDateTimeMap = resolveEffectiveDateTimes(steps);

            // Build Protocol Journey (one row per protocol-defined action)
            List<PatientTimelineDto.JourneyStep> journey = buildJourney(orderedActions, steps, stepTitles, effectiveDateTimeMap);

            // Build Compliance Timeline events
            List<PatientTimelineDto.TimelineEvent> events = new ArrayList<>();

            events.add(PatientTimelineDto.TimelineEvent.builder()
                    .timestamp(pi.getEnrolledAt())
                    .type("enrollment")
                    .state("ENROLLED")
                    .description("Enrolled in " + pi.getProtocolCanonical())
                    .build());

            for (StepInstance si : steps) {
                String stepName = stepTitles.getOrDefault(si.getActionId(), formatActionId(si.getActionId()));
                OffsetDateTime ts = resolveTimestamp(si);
                String type = "step_" + si.getState().name().toLowerCase();

                PatientTimelineDto.TimelineEvent.TimelineEventBuilder builder =
                        PatientTimelineDto.TimelineEvent.builder()
                                .timestamp(ts)
                                .type(type)
                                .actionId(si.getActionId())
                                .stepName(stepName)
                                .state(si.getState().name())
                                .effectiveDateTime(effectiveDateTimeMap.get(si.getId()));

                if (si.getState() == StepState.COMPLETED) {
                    builder.completionStatus(si.getCompletionStatus() != null ?
                            si.getCompletionStatus().name() : null);
                    builder.source(si.getCompletedBySource());
                } else if (si.getState() == StepState.OVERDUE && si.getDueDate() != null) {
                    int daysOverdue = (int) ChronoUnit.DAYS.between(si.getDueDate(), OffsetDateTime.now());
                    builder.daysOverdue(Math.max(daysOverdue, 0));
                }

                events.add(builder.build());
            }

            events.sort(Comparator.comparing(PatientTimelineDto.TimelineEvent::getTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            protocols.add(PatientTimelineDto.ProtocolTimeline.builder()
                    .protocolInstanceId(pi.getId().toString())
                    .protocolCanonical(pi.getProtocolCanonical())
                    .status(pi.getStatus().name().toLowerCase())
                    .complianceRate(Math.round(rate * 100.0) / 100.0)
                    .journey(journey)
                    .timeline(events)
                    .build());
        }

        return PatientTimelineDto.builder()
                .patientId(patientId)
                .protocols(protocols)
                .build();
    }

    /**
     * Build a consolidated journey: one row per protocol-defined action, in definition order.
     * Shows the latest/best status for each action.
     */
    private List<PatientTimelineDto.JourneyStep> buildJourney(
            List<String[]> orderedActions, List<StepInstance> steps,
            Map<String, String> stepTitles, Map<UUID, String> effectiveDateTimeMap) {

        // Group steps by actionId
        Map<String, List<StepInstance>> stepsByAction = steps.stream()
                .collect(Collectors.groupingBy(StepInstance::getActionId, LinkedHashMap::new, Collectors.toList()));

        List<PatientTimelineDto.JourneyStep> journey = new ArrayList<>();
        for (String[] action : orderedActions) {
            String actionId = action[0];
            String title = action[1];
            int depth = action.length > 2 ? Integer.parseInt(action[2]) : 0;
            String parentActionId = action.length > 3 && !action[3].isEmpty() ? action[3] : null;
            List<StepInstance> actionSteps = stepsByAction.getOrDefault(actionId, List.of());

            if (actionSteps.isEmpty()) {
                // No step_instance exists for this action
                journey.add(PatientTimelineDto.JourneyStep.builder()
                        .actionId(actionId)
                        .parentActionId(parentActionId)
                        .stepName(title)
                        .status("NOT_STARTED")
                        .completionCount(0)
                        .depth(depth)
                        .build());
            } else {
                // Pick the "best" status: COMPLETED > OVERDUE > MISSED > PENDING > SKIPPED
                StepInstance best = pickBestStep(actionSteps);
                int completedCount = (int) actionSteps.stream()
                        .filter(s -> s.getState() == StepState.COMPLETED)
                        .count();
                String effectiveDt = effectiveDateTimeMap.get(best.getId());
                // For completed, prefer the first completion's effectiveDateTime
                if (best.getState() == StepState.COMPLETED && effectiveDt == null) {
                    actionSteps.stream()
                            .filter(s -> s.getState() == StepState.COMPLETED)
                            .map(s -> effectiveDateTimeMap.get(s.getId()))
                            .filter(Objects::nonNull)
                            .findFirst()
                            .ifPresent(dt -> {});
                    effectiveDt = actionSteps.stream()
                            .filter(s -> s.getState() == StepState.COMPLETED)
                            .map(s -> effectiveDateTimeMap.get(s.getId()))
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                }

                journey.add(PatientTimelineDto.JourneyStep.builder()
                        .actionId(actionId)
                        .parentActionId(parentActionId)
                        .stepName(title)
                        .status(best.getState().name())
                        .completionCount(completedCount)
                        .effectiveDateTime(effectiveDt)
                        .completionStatus(best.getCompletionStatus() != null ? best.getCompletionStatus().name() : null)
                        .source(best.getCompletedBySource())
                        .depth(depth)
                        .build());
            }
        }
        return journey;
    }

    /**
     * Pick the most representative step for a given action.
     * Priority: COMPLETED > OVERDUE > MISSED > PENDING > DUE > SKIPPED
     */
    private StepInstance pickBestStep(List<StepInstance> steps) {
        Map<StepState, Integer> priority = Map.of(
                StepState.COMPLETED, 0,
                StepState.OVERDUE, 1,
                StepState.MISSED, 2,
                StepState.PENDING, 3,
                StepState.DUE, 4,
                StepState.SKIPPED, 5
        );
        return steps.stream()
                .min(Comparator.comparingInt(s -> priority.getOrDefault(s.getState(), 99)))
                .orElse(steps.get(0));
    }

    /**
     * Resolve effectiveDateTime for all steps that have a matched_event_id.
     * Returns a map of stepInstance.id → effectiveDateTime string.
     */
    private Map<UUID, String> resolveEffectiveDateTimes(List<StepInstance> steps) {
        Map<UUID, String> result = new HashMap<>();
        // Collect all matched event IDs
        Map<UUID, UUID> stepToEvent = new LinkedHashMap<>();
        for (StepInstance si : steps) {
            if (si.getMatchedEventId() != null) {
                stepToEvent.put(si.getId(), si.getMatchedEventId());
            }
        }
        if (stepToEvent.isEmpty()) return result;

        // Batch load event_log entries
        List<EventLog> eventLogs = eventLogRepository.findAllById(stepToEvent.values().stream().distinct().collect(Collectors.toList()));
        Map<UUID, EventLog> eventMap = eventLogs.stream().collect(Collectors.toMap(EventLog::getId, e -> e));

        for (Map.Entry<UUID, UUID> entry : stepToEvent.entrySet()) {
            EventLog el = eventMap.get(entry.getValue());
            if (el != null && el.getData() != null) {
                String effectiveDt = extractEffectiveDateTime(el.getData());
                if (effectiveDt != null) {
                    result.put(entry.getKey(), effectiveDt);
                }
            }
        }
        return result;
    }

    /**
     * Extract effectiveDateTime (or period.start for Encounter) from FHIR JSON data.
     */
    private String extractEffectiveDateTime(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode effectiveDt = root.get("effectiveDateTime");
            if (effectiveDt != null && !effectiveDt.isNull()) {
                return effectiveDt.asText();
            }
            // Fallback: Encounter.period.start
            JsonNode periodStart = root.path("period").get("start");
            if (periodStart != null && !periodStart.isNull()) {
                return periodStart.asText();
            }
            // Fallback: authoredOn (ServiceRequest)
            JsonNode authoredOn = root.get("authoredOn");
            if (authoredOn != null && !authoredOn.isNull()) {
                return authoredOn.asText();
            }
        } catch (Exception e) {
            log.debug("Failed to extract effectiveDateTime: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Resolve ordered action list from PlanDefinition JSON.
     * Returns list of [actionId, title, depth] tuples in definition order, recursing into nested actions.
     */
    private List<String[]> resolveOrderedActions(UUID protocolDefinitionId) {
        List<String[]> actions = new ArrayList<>();
        if (protocolDefinitionId == null) return actions;
        try {
            ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId).orElse(null);
            if (pd != null && pd.getDefinition() != null) {
                JsonNode root = objectMapper.readTree(pd.getDefinition());
                JsonNode actionNodes = root.get("action");
                if (actionNodes != null && actionNodes.isArray()) {
                    collectActions(actionNodes, actions, 0, null);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse protocol definition {}: {}", protocolDefinitionId, e.getMessage());
        }
        return actions;
    }

    private void collectActions(JsonNode actionNodes, List<String[]> actions, int depth, String parentId) {
        for (JsonNode action : actionNodes) {
            String id = action.has("id") ? action.get("id").asText() : null;
            String title = action.has("title") ? action.get("title").asText() : null;

            // Skip fire-event intelligence actions (notifications/escalations) — not compliance steps
            if (isFireEventAction(action)) {
                continue;
            }

            if (id != null) {
                actions.add(new String[]{id, title != null ? title : formatActionId(id), String.valueOf(depth), parentId != null ? parentId : ""});
            }
            // Recurse into nested sub-actions
            JsonNode subActions = action.get("action");
            if (subActions != null && subActions.isArray()) {
                collectActions(subActions, actions, depth + 1, id);
            }
        }
    }

    private boolean isFireEventAction(JsonNode action) {
        JsonNode type = action.get("type");
        if (type == null) return false;
        JsonNode coding = type.get("coding");
        if (coding == null || !coding.isArray()) return false;
        for (JsonNode c : coding) {
            if (c.has("code") && "fire-event".equals(c.get("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private OffsetDateTime resolveTimestamp(StepInstance si) {
        if (si.getCompletedAt() != null) return si.getCompletedAt();
        if (si.getOverdueDate() != null) return si.getOverdueDate();
        if (si.getMissedDate() != null) return si.getMissedDate();
        if (si.getDueDate() != null) return si.getDueDate();
        return null;
    }

    private String formatActionId(String actionId) {
        if (actionId == null) return "Unknown Step";
        return Arrays.stream(actionId.split("-"))
                .map(w -> w.substring(0, 1).toUpperCase() + w.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(actionId);
    }
}
