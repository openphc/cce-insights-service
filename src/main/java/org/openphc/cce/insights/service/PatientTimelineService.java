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

            // Build event context lookup: stepInstance.matchedEventId → EventContext (effectiveDateTime, practitioner, facilityId)
            Map<UUID, EventContext> eventContextMap = resolveEventContext(steps);

            // Resolve deviations for this protocol instance
            List<Deviation> deviations = deviationRepository.findByProtocolInstanceId(pi.getId());
            Map<String, Deviation> deviationByActionId = resolveDeviationsByActionId(deviations, steps);

            // Build Protocol Journey (one row per protocol-defined action)
            List<PatientTimelineDto.JourneyStep> journey = buildJourney(orderedActions, steps, stepTitles, eventContextMap, deviationByActionId);

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
                                .effectiveDateTime(eventContextMap.containsKey(si.getId()) ?
                                        eventContextMap.get(si.getId()).effectiveDateTime() : null);

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
            Map<String, String> stepTitles, Map<UUID, EventContext> eventContextMap,
            Map<String, Deviation> deviationByActionId) {

        // Group steps by actionId
        Map<String, List<StepInstance>> stepsByAction = steps.stream()
                .collect(Collectors.groupingBy(StepInstance::getActionId, LinkedHashMap::new, Collectors.toList()));

        List<PatientTimelineDto.JourneyStep> journey = new ArrayList<>();
        for (String[] action : orderedActions) {
            String actionId = action[0];
            String title = action[1];
            int depth = action.length > 2 ? Integer.parseInt(action[2]) : 0;
            String parentActionId = action.length > 3 && !action[3].isEmpty() ? action[3] : null;
            String requiredBehavior = action.length > 4 && !action[4].isEmpty() ? action[4] : null;
            List<StepInstance> actionSteps = stepsByAction.getOrDefault(actionId, List.of());

            if (actionSteps.isEmpty()) {
                // No step_instance exists for this action
                journey.add(PatientTimelineDto.JourneyStep.builder()
                        .actionId(actionId)
                        .parentActionId(parentActionId)
                        .stepName(title)
                        .status("NOT_STARTED")
                        .completionCount(0)
                        .requiredBehavior(requiredBehavior)
                        .depth(depth)
                        .build());
            } else {
                // Pick the "best" status: COMPLETED > OVERDUE > MISSED > PENDING > SKIPPED
                StepInstance best = pickBestStep(actionSteps);
                int completedCount = (int) actionSteps.stream()
                        .filter(s -> s.getState() == StepState.COMPLETED)
                        .count();
                EventContext ctx = eventContextMap.get(best.getId());
                // For completed, prefer the first completion's context
                if (best.getState() == StepState.COMPLETED && ctx == null) {
                    ctx = actionSteps.stream()
                            .filter(s -> s.getState() == StepState.COMPLETED)
                            .map(s -> eventContextMap.get(s.getId()))
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
                        .effectiveDateTime(ctx != null ? ctx.effectiveDateTime : null)
                        .dueDate(best.getDueDate() != null ? best.getDueDate().toString() : null)
                        .completionStatus(best.getCompletionStatus() != null ? best.getCompletionStatus().name() : null)
                        .source(best.getCompletedBySource())
                        .practitioner(ctx != null ? ctx.practitioner : null)
                        .facilityId(ctx != null ? ctx.facilityId : null)
                        .facilityName(ctx != null ? ctx.facilityName : null)
                        .requiredBehavior(requiredBehavior)
                        .depth(depth)
                        .description(resolveDeviationDescription(actionId, deviationByActionId, best))
                        .build());
            }
        }
        return journey;
    }

    /**
     * Map deviations to their corresponding actionId via stepInstanceId lookup.
     */
    private Map<String, Deviation> resolveDeviationsByActionId(List<Deviation> deviations, List<StepInstance> steps) {
        Map<UUID, String> stepIdToAction = steps.stream()
                .collect(Collectors.toMap(StepInstance::getId, StepInstance::getActionId, (a, b) -> a));
        Map<String, Deviation> result = new HashMap<>();
        for (Deviation d : deviations) {
            String actionId = stepIdToAction.get(d.getStepInstanceId());
            if (actionId != null) {
                result.putIfAbsent(actionId, d);
            }
        }
        return result;
    }

    /**
     * Resolve a human-readable description for a deviation on this action, or null if none.
     */
    private String resolveDeviationDescription(String actionId, Map<String, Deviation> deviationByActionId, StepInstance best) {
        Deviation dev = deviationByActionId.get(actionId);
        if (dev == null) return null;
        return switch (dev.getDeviationType()) {
            case OVERDUE -> best.getCompletionStatus() != null && "LATE".equals(best.getCompletionStatus().name())
                    ? "Completed after SLA window"
                    : "Step overdue — exceeded expected timeframe";
            case MISSED -> "Step missed — no completion recorded within window";
            case ORDER_VIOLATION -> "Completed out of expected protocol order";
        };
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
     * Resolve event context (effectiveDateTime, practitioner, facilityId) for all steps with a matched_event_id.
     * Returns a map of stepInstance.id → EventContext.
     */
    private Map<UUID, EventContext> resolveEventContext(List<StepInstance> steps) {
        Map<UUID, EventContext> result = new HashMap<>();
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
            if (el != null) {
                String effectiveDt = el.getData() != null ? extractEffectiveDateTime(el.getData()) : null;
                String practitioner = el.getData() != null ? extractPractitioner(el.getData()) : null;
                String facilityId = el.getFacilityId();
                String facilityName = el.getData() != null ? extractFacilityName(el.getData()) : null;
                if (effectiveDt != null || practitioner != null || facilityId != null || facilityName != null) {
                    result.put(entry.getKey(), new EventContext(effectiveDt, practitioner, facilityId, facilityName));
                }
            }
        }
        return result;
    }

    private record EventContext(String effectiveDateTime, String practitioner, String facilityId, String facilityName) {}

    /**
     * Extract practitioner display name from FHIR JSON data.
     * Supports: Encounter.participant[].individual, Observation.performer[], Condition.asserter
     */
    private String extractPractitioner(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            // Encounter: participant[].individual — prefer Practitioner reference
            JsonNode participants = root.get("participant");
            if (participants != null && participants.isArray()) {
                String fallback = null;
                for (JsonNode p : participants) {
                    JsonNode individual = p.get("individual");
                    if (individual != null) {
                        String ref = individual.has("reference") ? individual.get("reference").asText() : null;
                        if (ref != null && ref.startsWith("Practitioner/")) {
                            return extractDisplayOrReference(individual);
                        }
                        if (fallback == null) {
                            fallback = extractDisplayOrReference(individual);
                        }
                    }
                }
                if (fallback != null) return fallback;
            }
            // Observation: performer[] — prefer Practitioner reference
            JsonNode performers = root.get("performer");
            if (performers != null && performers.isArray()) {
                String fallback = null;
                for (JsonNode perf : performers) {
                    String ref = perf.has("reference") ? perf.get("reference").asText() : null;
                    if (ref != null && ref.startsWith("Practitioner/")) {
                        return extractDisplayOrReference(perf);
                    }
                    if (fallback == null) {
                        fallback = extractDisplayOrReference(perf);
                    }
                }
                if (fallback != null) return fallback;
            }
            // Condition: asserter.display or reference
            JsonNode asserter = root.get("asserter");
            if (asserter != null) {
                String name = extractDisplayOrReference(asserter);
                if (name != null) return name;
            }
        } catch (Exception e) {
            log.debug("Failed to extract practitioner: {}", e.getMessage());
        }
        return null;
    }

    private String extractDisplayOrReference(JsonNode node) {
        if (node.has("display")) {
            return node.get("display").asText();
        }
        if (node.has("reference")) {
            return node.get("reference").asText();
        }
        return null;
    }

    /**
     * Extract facility/location name from FHIR JSON data.
     * Supports: ServiceRequest.locationReference[], Encounter.location[].location
     */
    private String extractFacilityName(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            // ServiceRequest: locationReference[].display
            JsonNode locationRef = root.get("locationReference");
            if (locationRef != null && locationRef.isArray()) {
                for (JsonNode loc : locationRef) {
                    if (loc.has("display")) {
                        return loc.get("display").asText();
                    }
                    if (loc.has("reference")) {
                        return loc.get("reference").asText();
                    }
                }
            }
            // Encounter: location[].location.display
            JsonNode locations = root.get("location");
            if (locations != null && locations.isArray()) {
                for (JsonNode loc : locations) {
                    JsonNode location = loc.get("location");
                    if (location != null && location.has("display")) {
                        return location.get("display").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract facility name: {}", e.getMessage());
        }
        return null;
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
            // Fallback: meta.lastUpdated
            JsonNode lastUpdated = root.path("meta").get("lastUpdated");
            if (lastUpdated != null && !lastUpdated.isNull()) {
                return lastUpdated.asText();
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
            String requiredBehavior = action.has("requiredBehavior") ? action.get("requiredBehavior").asText() : null;

            // Skip fire-event intelligence actions (notifications/escalations) — not compliance steps
            if (isFireEventAction(action)) {
                continue;
            }

            if (id != null) {
                actions.add(new String[]{id, title != null ? title : formatActionId(id), String.valueOf(depth), parentId != null ? parentId : "", requiredBehavior != null ? requiredBehavior : ""});
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
