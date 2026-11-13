package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.insights.domain.entity.*;
import org.openphc.cce.insights.domain.enums.SlaStatus;
import org.openphc.cce.insights.domain.enums.StepStatus;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientTimelineService {

    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final StepGroupInstanceRepository stepGroupInstanceRepository;
    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final DeviationRepository deviationRepository;
    private final MatcherEventLogRepository matcherEventLogRepository;
    private final DailyKpiRepository dailyKpiRepository;
    private final ObjectMapper objectMapper;

    public PatientTimelineDto getTimeline(String patientId,
                                          OffsetDateTime startDate, OffsetDateTime endDate) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);
        List<UUID> instanceIds = instances.stream().map(ProtocolInstance::getId).collect(Collectors.toList());

        // Batch load steps and deviations in 2 queries (replaces N+1 per protocol instance)
        Map<UUID, List<StepInstance>> stepsByInstance = stepInstanceRepository
                .findByProtocolInstanceIdIn(instanceIds)
                .stream()
                .sorted(Comparator.comparing(StepInstance::getDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(StepInstance::getProtocolInstanceId));
        Map<UUID, List<Deviation>> deviationsByInstance = deviationRepository
                .findByProtocolInstanceIdIn(instanceIds)
                .stream()
                .collect(Collectors.groupingBy(Deviation::getProtocolInstanceId));
        Map<UUID, List<StepGroupInstance>> groupInstancesByInstance = stepGroupInstanceRepository
                .findByProtocolInstanceIdIn(instanceIds)
                .stream()
                .collect(Collectors.groupingBy(StepGroupInstance::getProtocolInstanceId));

        // facility_id -> facility_name, for steps whose backing FHIR resource type has no
        // resolvable name field of its own (see resolveEventContext / extractFacilityName).
        Map<String, String> facilityNameMap = new HashMap<>();
        for (Object[] row : dailyKpiRepository.getFacilityReference()) {
            facilityNameMap.put((String) row[0], (String) row[1]);
        }

        List<PatientTimelineDto.ProtocolTimeline> protocols = new ArrayList<>();
        for (ProtocolInstance pi : instances) {
            List<StepInstance> steps = stepsByInstance.getOrDefault(pi.getId(), List.of());
            long completed = steps.stream().filter(StepInstance::isCompleted).count();
            double rate = steps.isEmpty() ? 0.0 : (double) completed / steps.size();

            // Resolve ordered action list and titles from protocol definition
            List<ProtocolAction> orderedActions = resolveOrderedActions(pi.getProtocolDefinitionId());
            Map<String, String> stepTitles = new LinkedHashMap<>();
            for (ProtocolAction action : orderedActions) {
                stepTitles.put(action.actionId(), action.title());
            }
            StepGroupProgress groupProgress = new StepGroupProgress(
                    groupInstancesByInstance.getOrDefault(pi.getId(), List.of()), steps);

            // Build event context lookup: stepInstance.matchedEventId → EventContext (practitioner, facilityId, facilityName)
            Map<UUID, EventContext> eventContextMap = resolveEventContext(steps, facilityNameMap);

            // Deviations already loaded in batch above
            List<Deviation> deviations = deviationsByInstance.getOrDefault(pi.getId(), List.of());
            Map<String, Deviation> deviationByActionId = resolveDeviationsByActionId(deviations, steps);
            Map<UUID, Deviation> deviationByStepInstanceId = resolveDeviationsByStepInstanceId(deviations);

            // Build Protocol Journey (one row per protocol-defined action)
            List<PatientTimelineDto.JourneyStep> journey = buildJourney(orderedActions, steps, groupProgress,
                    eventContextMap, deviationByActionId, deviationByStepInstanceId);

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
                String displayStatus = si.displayStatus();
                String type = "step_" + displayStatus.toLowerCase();

                PatientTimelineDto.TimelineEvent.TimelineEventBuilder builder =
                        PatientTimelineDto.TimelineEvent.builder()
                                .timestamp(ts)
                                .type(type)
                                .actionId(si.getActionId())
                                .stepName(stepName)
                                .state(displayStatus)
                                .stepStatus(si.getStepStatus() != null ? si.getStepStatus().name() : null)
                                .slaStatus(si.getSlaStatus() != null ? si.getSlaStatus().name() : null)
                                .effectiveDateTime(completionTime(si));
                groupProgress.groupInstanceOf(si).ifPresent(groupInstance -> builder
                        .groupActionId(groupInstance.getGroupActionId())
                        .cycleIndex(groupInstance.getCycleIndex()));

                if (si.isCompleted()) {
                    builder.source(si.getCompletedBySource());
                } else if (si.getSlaStatus() == SlaStatus.OVERDUE && si.getDueDate() != null) {
                    int daysOverdue = (int) ChronoUnit.DAYS.between(si.getDueDate(), OffsetDateTime.now());
                    builder.daysOverdue(Math.max(daysOverdue, 0));
                }

                events.add(builder.build());
            }

            events.sort(Comparator.comparing(PatientTimelineDto.TimelineEvent::getTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            // Apply date filter to the timeline (only — Protocol Journey shows the full
            // protocol step list regardless of when each step happened, so filtering
            // there would hide structure rather than narrow time scope).
            if (startDate != null || endDate != null) {
                events = events.stream()
                        .filter(e -> {
                            OffsetDateTime t = e.getTimestamp();
                            if (t == null) return true; // never-fired steps stay visible
                            if (startDate != null && t.isBefore(startDate)) return false;
                            if (endDate != null && t.isAfter(endDate)) return false;
                            return true;
                        })
                        .collect(Collectors.toList());
            }

            protocols.add(PatientTimelineDto.ProtocolTimeline.builder()
                    .protocolInstanceId(pi.getId().toString())
                    .protocolCanonical(pi.getProtocolCanonical())
                    .status(pi.getStatus().name().toLowerCase())
                    .complianceRate(Math.round(rate * 1000.0) / 10.0)
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
     * Shows the latest/best status for each action. A step group gets a row of its own, with its
     * instances (see {@link #journeyRowForGroup}). A step's row also lists its step instances, each
     * with its group instance, so a client can show one cycle of a group at a time.
     */
    private List<PatientTimelineDto.JourneyStep> buildJourney(
            List<ProtocolAction> orderedActions, List<StepInstance> steps,
            StepGroupProgress groupProgress, Map<UUID, EventContext> eventContextMap,
            Map<String, Deviation> deviationByActionId, Map<UUID, Deviation> deviationByStepInstanceId) {

        // Group steps by actionId
        Map<String, List<StepInstance>> stepsByAction = steps.stream()
                .collect(Collectors.groupingBy(StepInstance::getActionId, LinkedHashMap::new, Collectors.toList()));

        List<PatientTimelineDto.JourneyStep> journey = new ArrayList<>();
        for (ProtocolAction action : orderedActions) {
            if (action.group()) {
                journey.add(journeyRowForGroup(action, groupProgress));
                continue;
            }
            String actionId = action.actionId();
            String title = action.title();
            int depth = action.depth();
            String parentActionId = action.parentActionId();
            String requiredBehavior = action.requiredBehavior();
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
                        .instances(List.of())
                        .build());
            } else {
                // Pick the "best" status: COMPLETED > OVERDUE > MISSED > NOT_STARTED
                StepInstance best = pickBestStep(actionSteps);
                int completedCount = (int) actionSteps.stream()
                        .filter(StepInstance::isCompleted)
                        .count();
                EventContext ctx = eventContextMap.get(best.getId());
                // For completed, prefer the first completion's context
                if (best.isCompleted() && ctx == null) {
                    ctx = actionSteps.stream()
                            .filter(StepInstance::isCompleted)
                            .map(s -> eventContextMap.get(s.getId()))
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                }

                journey.add(PatientTimelineDto.JourneyStep.builder()
                        .actionId(actionId)
                        .parentActionId(parentActionId)
                        .stepName(title)
                        .status(best.displayStatus())
                        .stepStatus(best.getStepStatus() != null ? best.getStepStatus().name() : null)
                        .slaStatus(best.getSlaStatus() != null ? best.getSlaStatus().name() : null)
                        .completionCount(completedCount)
                        .effectiveDateTime(completionTime(best))
                        .dueDate(best.getDueDate() != null ? best.getDueDate().toString() : null)
                        .source(best.getCompletedBySource())
                        .practitioner(ctx != null ? ctx.practitioner : null)
                        .facilityId(ctx != null ? ctx.facilityId : null)
                        .facilityName(ctx != null ? ctx.facilityName : null)
                        .requiredBehavior(requiredBehavior)
                        .depth(depth)
                        .description(resolveDeviationDescription(actionId, deviationByActionId, best))
                        .instances(actionSteps.stream()
                                .map(stepInstance -> toJourneyStepInstance(stepInstance, eventContextMap,
                                        deviationByStepInstanceId))
                                .toList())
                        .build());
            }
        }
        return journey;
    }

    /**
     * The journey row of a step group, such as a visit. A group never matches an event, so it has
     * no step instances of its own. Its row shows its instances instead, each with how many of its
     * steps are done. The row's status is that of its best instance, like a step's
     * ({@link StepGroupProgress#displayStatus}). A group that never opened is NOT_STARTED, with no
     * stepStatus, like a step with no instance.
     */
    private PatientTimelineDto.JourneyStep journeyRowForGroup(ProtocolAction action, StepGroupProgress groupProgress) {
        List<StepGroupInstance> groupInstances = groupProgress.instancesOf(action.actionId());
        PatientTimelineDto.JourneyStep.JourneyStepBuilder row = PatientTimelineDto.JourneyStep.builder()
                .actionId(action.actionId())
                .parentActionId(action.parentActionId())
                .stepName(action.title())
                .requiredBehavior(action.requiredBehavior())
                .depth(action.depth())
                .group(true)
                .groupInstances(groupInstances.stream()
                        .map(groupInstance -> toGroupInstanceDto(groupInstance, groupProgress))
                        .toList());

        if (groupInstances.isEmpty()) {
            return row.status(StepStatus.NOT_STARTED.name()).completionCount(0).build();
        }
        StepGroupInstance best = groupInstances.stream()
                .min(Comparator.comparingInt(g -> displayStatusPriority(groupProgress.displayStatus(g))))
                .orElseThrow();
        String status = groupProgress.displayStatus(best);
        return row.status(status)
                .stepStatus(StepStatus.COMPLETED.name().equals(status)
                        ? StepStatus.COMPLETED.name() : StepStatus.NOT_STARTED.name())
                .slaStatus(best.getSlaStatus() != null ? best.getSlaStatus().name() : null)
                .completionCount((int) groupInstances.stream().filter(groupProgress::isDone).count())
                .dueDate(best.getDueDate() != null ? best.getDueDate().toString() : null)
                .build();
    }

    private PatientTimelineDto.GroupInstance toGroupInstanceDto(StepGroupInstance groupInstance,
                                                                StepGroupProgress groupProgress) {
        List<StepInstance> stepInstances = groupProgress.stepInstancesIn(groupInstance);
        return PatientTimelineDto.GroupInstance.builder()
                .groupInstanceId(groupInstance.getId().toString())
                .parentGroupInstanceId(groupInstance.getParentGroupInstanceId() != null
                        ? groupInstance.getParentGroupInstanceId().toString() : null)
                .cycleIndex(groupInstance.getCycleIndex())
                .status(groupProgress.displayStatus(groupInstance))
                .openedAt(groupInstance.getOpenedAt())
                .dueDate(groupInstance.getDueDate())
                .completedAt(groupProgress.doneAt(groupInstance))
                .completedSteps((int) stepInstances.stream().filter(StepInstance::isCompleted).count())
                .totalSteps(stepInstances.size())
                .build();
    }

    /** One step instance of a step's journey row, with the group instance it is in. */
    private PatientTimelineDto.JourneyStepInstance toJourneyStepInstance(
            StepInstance stepInstance, Map<UUID, EventContext> eventContextMap,
            Map<UUID, Deviation> deviationByStepInstanceId) {
        EventContext ctx = eventContextMap.get(stepInstance.getId());
        Deviation deviation = deviationByStepInstanceId.get(stepInstance.getId());
        return PatientTimelineDto.JourneyStepInstance.builder()
                .stepInstanceId(stepInstance.getId().toString())
                .groupInstanceId(stepInstance.getStepGroupInstanceId() != null
                        ? stepInstance.getStepGroupInstanceId().toString() : null)
                .status(stepInstance.displayStatus())
                .stepStatus(stepInstance.getStepStatus() != null ? stepInstance.getStepStatus().name() : null)
                .slaStatus(stepInstance.getSlaStatus() != null ? stepInstance.getSlaStatus().name() : null)
                .effectiveDateTime(completionTime(stepInstance))
                .dueDate(stepInstance.getDueDate() != null ? stepInstance.getDueDate().toString() : null)
                .source(stepInstance.getCompletedBySource())
                .practitioner(ctx != null ? ctx.practitioner : null)
                .facilityId(ctx != null ? ctx.facilityId : null)
                .facilityName(ctx != null ? ctx.facilityName : null)
                .description(deviation != null ? describeDeviation(deviation, stepInstance) : null)
                .build();
    }

    /** Each step instance's deviation, the first when it has several. */
    private Map<UUID, Deviation> resolveDeviationsByStepInstanceId(List<Deviation> deviations) {
        Map<UUID, Deviation> result = new HashMap<>();
        for (Deviation d : deviations) {
            if (d.getStepInstanceId() != null) {
                result.putIfAbsent(d.getStepInstanceId(), d);
            }
        }
        return result;
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
        return describeDeviation(dev, best);
    }

    /** A deviation in words, for the step instance it is shown with. */
    private String describeDeviation(Deviation dev, StepInstance stepInstance) {
        return switch (dev.getDeviationType()) {
            case OVERDUE -> stepInstance.isCompleted()
                    ? "Completed after SLA window"
                    : "Step overdue — exceeded expected timeframe";
            case MISSED -> "Step missed — no completion recorded within window";
            case ORDER_VIOLATION -> "Completed out of expected protocol order";
        };
    }

    /**
     * Pick the most representative step for a given action, by display status.
     * Priority: COMPLETED > OVERDUE > MISSED > NOT_STARTED
     */
    private StepInstance pickBestStep(List<StepInstance> steps) {
        return steps.stream()
                .min(Comparator.comparingInt(s -> displayStatusPriority(s.displayStatus())))
                .orElse(steps.get(0));
    }

    /** COMPLETED > OVERDUE > MISSED > NOT_STARTED, for a step or a group instance. */
    private static int displayStatusPriority(String displayStatus) {
        return switch (displayStatus) {
            case "COMPLETED" -> 0;
            case "OVERDUE" -> 1;
            case "MISSED" -> 2;
            case "NOT_STARTED" -> 3;
            default -> 99;
        };
    }

    /**
     * Resolve event context (practitioner, facilityId, facilityName) for completed steps.
     * Uses step_instance.matched_event_id → matcher_event_logs → inbound_event_logs.
     * Returns a map of stepInstance.id → EventContext.
     */
    private Map<UUID, EventContext> resolveEventContext(List<StepInstance> steps, Map<String, String> facilityNameMap) {
        Map<UUID, EventContext> result = new HashMap<>();
        Map<UUID, UUID> stepToEvent = new LinkedHashMap<>();
        for (StepInstance si : steps) {
            if (si.getMatchedEventId() != null) {
                stepToEvent.put(si.getId(), si.getMatchedEventId());
            }
        }
        if (stepToEvent.isEmpty()) return result;

        List<MatcherEventLog> eventLogs = matcherEventLogRepository.findByMatcherEventIds(
                stepToEvent.values().stream().distinct().collect(Collectors.toList()));
        Map<UUID, MatcherEventLog> eventMap = eventLogs.stream().collect(Collectors.toMap(MatcherEventLog::getId, e -> e));

        for (Map.Entry<UUID, UUID> entry : stepToEvent.entrySet()) {
            MatcherEventLog el = eventMap.get(entry.getValue());
            if (el != null) {
                String practitioner = el.getData() != null ? extractPractitioner(el.getData()) : null;
                String facilityId = resolveFacilityId(el);
                String facilityName = el.getData() != null ? extractFacilityName(el.getData(), facilityId) : null;
                // extractFacilityName only resolves a display name for FHIR resource types that carry
                // one (Encounter); other types (e.g. Observation) only have the source-facility
                // extension id, so fall back to the facility dimension lookup by that id.
                if (facilityName == null && facilityId != null) {
                    facilityName = facilityNameMap.get(facilityId);
                }
                if (practitioner != null || facilityId != null || facilityName != null) {
                    result.put(entry.getKey(), new EventContext(practitioner, facilityId, facilityName));
                }
            }
        }
        return result;
    }

    private record EventContext(String practitioner, String facilityId, String facilityName) {}

    /**
     * When a completed step's work happened: its completed_at, which Matcher takes from the event's
     * clinical time (the time in the FHIR payload, e.g. Condition.recordedDate). It is the same time
     * the step's SLA and its visit were judged by, whatever the resource type. Null for a step not
     * completed.
     */
    private static String completionTime(StepInstance stepInstance) {
        return stepInstance.isCompleted() && stepInstance.getCompletedAt() != null
                ? stepInstance.getCompletedAt().toString() : null;
    }

    /**
     * Resolves the facility id for an event: the ClickHouse-joined {@code facility_id} column when
     * present, otherwise derived directly from the FHIR body the same way FacilityService does
     * upstream (hospitalization.origin, then the source-facility extension).
     *
     * {@code findByMatcherEventIds} — the query backing this method — reads {@code
     * matcher_event_logs} without joining {@code inbound_event_logs}, and {@code
     * matcher_event_logs} carries no facility_id column of its own, so {@code
     * el.getFacilityId()} is currently always blank for this call path; the FHIR-derived fallback
     * below is therefore the only source of a facility id here, not a backup for a rare gap.
     */
    private String resolveFacilityId(MatcherEventLog el) {
        String stored = el.getFacilityId();
        if (stored != null && !stored.isBlank()) return stored;
        if (el.getData() == null) return null;
        try {
            JsonNode root = objectMapper.readTree(el.getData());
            JsonNode hospitalization = root.get("hospitalization");
            JsonNode origin = hospitalization != null ? hospitalization.get("origin") : null;
            if (origin != null) {
                String id = extractBareId(origin);
                if (id != null) return id;
            }
            return extractSourceFacilityExtension(root);
        } catch (Exception e) {
            log.debug("Failed to extract facilityId: {}", e.getMessage());
            return null;
        }
    }

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
     * Supports: ServiceRequest.locationReference[], Encounter.hospitalization.origin (transfers),
     * Encounter.location[].location (fallback, guarded against {@code knownFacilityId}).
     *
     * For a transfer Encounter, location[].location reflects where the patient ended up
     * (the destination), not where the encounter/referral originated — hospitalization.origin
     * is the correct source facility. When origin is absent, {@code knownFacilityId} (resolved by
     * {@link #resolveFacilityId}, itself falling back to the source-facility extension) is used to
     * confirm location[]'s id actually matches the source facility before trusting its display name
     * — otherwise it may be the transfer destination. Mirrors the openhim-cce-emitter-adaptor fix
     * (PR #30).
     *
     * @param knownFacilityId the facility id already resolved for this event (see {@link
     *                        #resolveFacilityId}), or null if unresolved — passed in rather than
     *                        re-derived here to avoid parsing the source-facility extension twice
     */
    private String extractFacilityName(String jsonData, String knownFacilityId) {
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
            // Encounter (transfer): hospitalization.origin.display takes priority
            JsonNode hospitalization = root.get("hospitalization");
            JsonNode origin = hospitalization != null ? hospitalization.get("origin") : null;
            if (origin != null) {
                String name = extractDisplayOrReference(origin);
                if (name != null) return name;
            }
            // Encounter: location[].location.display — only trusted when there's no known
            // facility id, or when it agrees with location[]'s own id.
            JsonNode locations = root.get("location");
            if (locations != null && locations.isArray()) {
                for (JsonNode loc : locations) {
                    JsonNode location = loc.get("location");
                    if (location != null && location.has("display")) {
                        if (knownFacilityId == null || knownFacilityId.equals(extractBareId(location))) {
                            return location.get("display").asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract facility name: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extracts the facility ID from the source system's {@code source-facility} extension
     * (matched by URL suffix so it survives base-URL changes), e.g.:
     * {@code {"url": ".../source-facility", "valueString": "1651"}} → {@code "1651"}.
     */
    private String extractSourceFacilityExtension(JsonNode root) {
        JsonNode extensions = root.get("extension");
        if (extensions == null || !extensions.isArray()) return null;
        for (JsonNode extension : extensions) {
            JsonNode urlNode = extension.get("url");
            String url = urlNode != null ? urlNode.asText() : null;
            if (url != null && url.endsWith("source-facility") && extension.has("valueString")) {
                String value = extension.get("valueString").asText();
                if (!value.isBlank()) return value;
            }
        }
        return null;
    }

    /**
     * Extracts the bare id (stripping any {@code ResourceType/} prefix) from a Reference node's
     * {@code reference} or {@code identifier.value}, for comparison against the source-facility
     * extension's value.
     */
    private String extractBareId(JsonNode refNode) {
        if (refNode.has("reference")) {
            String reference = refNode.get("reference").asText();
            return reference.contains("/") ? reference.substring(reference.lastIndexOf('/') + 1) : reference;
        }
        JsonNode identifier = refNode.get("identifier");
        if (identifier != null && identifier.has("value")) {
            return identifier.get("value").asText();
        }
        return null;
    }

    /** One action of the protocol: a step, or a step group (such as a visit). */
    private record ProtocolAction(String actionId, String title, int depth, String parentActionId,
                                  String requiredBehavior, boolean group) {}

    /**
     * Resolve ordered action list from PlanDefinition JSON, in definition order, recursing into
     * nested actions.
     */
    private List<ProtocolAction> resolveOrderedActions(UUID protocolDefinitionId) {
        List<ProtocolAction> actions = new ArrayList<>();
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

    private void collectActions(JsonNode actionNodes, List<ProtocolAction> actions, int depth, String parentId) {
        for (JsonNode action : actionNodes) {
            String id = action.has("id") ? action.get("id").asText() : null;
            String title = action.has("title") ? action.get("title").asText() : null;
            String requiredBehavior = action.has("requiredBehavior") ? action.get("requiredBehavior").asText() : null;

            // Skip fire-event intelligence actions (notifications/escalations) — not compliance steps
            if (PlanDefinitionActions.isFireEvent(action)) {
                continue;
            }

            if (id != null) {
                actions.add(new ProtocolAction(id, title != null ? title : formatActionId(id), depth, parentId,
                        requiredBehavior, PlanDefinitionActions.isStepGroup(action)));
            }
            // Recurse into nested sub-actions
            JsonNode subActions = action.get("action");
            if (subActions != null && subActions.isArray()) {
                collectActions(subActions, actions, depth + 1, id);
            }
        }
    }

    /**
     * Completion time, else the due date. (1.x fell back to overdue_date / missed_date first; in
     * 2.0.0 the first threshold an outstanding step breaches — DUE_DATE_REACHED — is its due date.)
     */
    private OffsetDateTime resolveTimestamp(StepInstance si) {
        if (si.getCompletedAt() != null) return si.getCompletedAt();
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
