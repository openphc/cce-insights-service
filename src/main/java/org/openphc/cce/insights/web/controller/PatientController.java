package org.openphc.cce.insights.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.entity.EventLog;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.EventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.service.PatientTimelineService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/v1/insights/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientTimelineService patientTimelineService;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final DeviationRepository deviationRepository;
    private final EventLogRepository eventLogRepository;
    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/{patientId}/compliance-timeline")
    public ResponseEntity<ApiResponse<PatientTimelineDto>> getComplianceTimeline(
            @PathVariable String patientId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        PatientTimelineDto timeline = patientTimelineService.getTimeline(patientId);
        return ResponseEntity.ok(ApiResponse.ok(timeline));
    }

    @GetMapping("/{patientId}/protocol-tracking")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProtocolTracking(
            @PathVariable String patientId) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);
        List<Map<String, Object>> result = instances.stream().map(pi -> {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
            long completed = steps.stream().filter(s -> s.getCompletedAt() != null).count();
            double rate = steps.isEmpty() ? 0 : Math.round((double) completed / steps.size() * 100.0) / 100.0;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("protocolInstanceId", pi.getId());
            map.put("protocolCanonical", pi.getProtocolCanonical());

            // Extract title and relatedArtifact from protocol definition JSONB
            String protocolTitle = pi.getProtocolCanonical();
            List<Map<String, Object>> relatedArtifacts = null;
            try {
                ProtocolDefinition pd = protocolDefinitionRepository.findById(pi.getProtocolDefinitionId()).orElse(null);
                if (pd != null && pd.getDefinition() != null) {
                    JsonNode root = objectMapper.readTree(pd.getDefinition());
                    JsonNode titleNode = root.get("title");
                    if (titleNode != null && !titleNode.isNull()) {
                        protocolTitle = titleNode.asText();
                    } else {
                        JsonNode nameNode = root.get("name");
                        if (nameNode != null && !nameNode.isNull()) {
                            protocolTitle = nameNode.asText();
                        }
                    }
                    JsonNode artifactsNode = root.get("relatedArtifact");
                    if (artifactsNode != null && artifactsNode.isArray()) {
                        relatedArtifacts = new ArrayList<>();
                        for (JsonNode artifact : artifactsNode) {
                            Map<String, Object> artMap = new LinkedHashMap<>();
                            if (artifact.has("type")) artMap.put("type", artifact.get("type").asText());
                            if (artifact.has("label")) artMap.put("label", artifact.get("label").asText());
                            if (artifact.has("display")) artMap.put("display", artifact.get("display").asText());
                            if (artifact.has("url")) artMap.put("url", artifact.get("url").asText());
                            if (artifact.has("extension") && artifact.get("extension").isArray()) {
                                List<Map<String, String>> extensions = new ArrayList<>();
                                for (JsonNode ext : artifact.get("extension")) {
                                    Map<String, String> extMap = new LinkedHashMap<>();
                                    if (ext.has("url")) extMap.put("url", ext.get("url").asText());
                                    if (ext.has("valueCode")) extMap.put("valueCode", ext.get("valueCode").asText());
                                    extensions.add(extMap);
                                }
                                artMap.put("extension", extensions);
                            }
                            relatedArtifacts.add(artMap);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse protocol definition for {}: {}", pi.getProtocolDefinitionId(), e.getMessage());
            }
            map.put("protocolTitle", protocolTitle);
            if (relatedArtifacts != null) {
                map.put("relatedArtifact", relatedArtifacts);
            }

            map.put("enrolledAt", pi.getEnrolledAt());
            map.put("status", pi.getStatus());
            map.put("complianceRate", rate);
            map.put("stepsCompleted", completed);
            map.put("totalSteps", steps.size());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{patientId}/protocol-tracking/{protocolInstanceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProtocolTrackingDetail(
            @PathVariable String patientId,
            @PathVariable UUID protocolInstanceId) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);
        ProtocolInstance pi = instances.stream()
                .filter(p -> p.getId().equals(protocolInstanceId))
                .findFirst()
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Protocol instance not found: " + protocolInstanceId));

        List<StepInstance> steps = stepInstanceRepository
                .findByProtocolInstanceIdOrderByDueDateAsc(protocolInstanceId);
        List<Deviation> deviations = deviationRepository.findByProtocolInstanceId(protocolInstanceId);

        long completed = steps.stream().filter(s -> s.getCompletedAt() != null).count();
        double rate = steps.isEmpty() ? 0 : Math.round((double) completed / steps.size() * 100.0) / 100.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolInstanceId", pi.getId());
        result.put("patientId", pi.getPatientId());
        result.put("protocolCanonical", pi.getProtocolCanonical());
        result.put("status", pi.getStatus());
        result.put("enrolledAt", pi.getEnrolledAt());
        result.put("complianceRate", rate);

        List<Map<String, Object>> stepList = steps.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("stepInstanceId", s.getId());
            sm.put("actionId", s.getActionId());
            sm.put("state", s.getState());
            sm.put("dueDate", s.getDueDate());
            if (s.getCompletedAt() != null) sm.put("completedAt", s.getCompletedAt());
            if (s.getCompletionStatus() != null) sm.put("completionStatus", s.getCompletionStatus());
            if (s.getCompletedBySource() != null) sm.put("completedBySource", s.getCompletedBySource());
            if (s.getOverdueDate() != null) sm.put("overdueDate", s.getOverdueDate());
            if (s.getMissedDate() != null) sm.put("missedDate", s.getMissedDate());
            return sm;
        }).collect(Collectors.toList());
        result.put("steps", stepList);

        List<Map<String, Object>> devList = deviations.stream().map(d -> {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("deviationId", d.getId());
            dm.put("stepInstanceId", d.getStepInstanceId());
            dm.put("deviationType", d.getDeviationType());
            dm.put("detectedAt", d.getDetectedAt());
            return dm;
        }).collect(Collectors.toList());
        result.put("deviations", devList);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{patientId}/events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPatientEvents(
            @PathVariable String patientId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit) {
        String subject = "Patient/" + patientId;
        List<EventLog> events = eventLogRepository.findBySubjectOrderByEventTimeDesc(subject);

        List<Map<String, Object>> result = events.stream()
                .filter(e -> resourceType == null || extractResourceType(e.getData()).equals(resourceType))
                .filter(e -> source == null || source.equals(e.getSource()))
                .filter(e -> startDate == null || !e.getEventTime().isBefore(startDate))
                .filter(e -> endDate == null || !e.getEventTime().isAfter(endDate))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("eventId", e.getId());
                    map.put("cloudeventsId", e.getCloudeventsId());
                    map.put("type", e.getType());
                    map.put("eventTime", e.getEventTime());
                    map.put("source", e.getSource());
                    map.put("resourceType", extractResourceType(e.getData()));
                    map.put("processingStatus", e.getProcessingStatus());
                    map.put("facilityId", e.getFacilityId());
                    if (e.getProtocolInstanceId() != null)
                        map.put("protocolInstanceId", e.getProtocolInstanceId());
                    if (e.getActionId() != null)
                        map.put("actionId", e.getActionId());
                    if (e.getMatchedStepInstanceId() != null)
                        map.put("matchedStepInstanceId", e.getMatchedStepInstanceId());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{patientId}/deviations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPatientDeviations(
            @PathVariable String patientId,
            @RequestParam(required = false) String deviationType,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);

        // Build actionId→title map from protocol definitions
        Map<String, String> stepTitles = new HashMap<>();
        for (ProtocolInstance pi : instances) {
            if (pi.getProtocolDefinitionId() != null) {
                resolveStepTitles(pi.getProtocolDefinitionId(), stepTitles);
            }
        }

        // Build stepInstanceId→actionId lookup
        Map<UUID, String> stepActionIds = new HashMap<>();
        for (ProtocolInstance pi : instances) {
            for (StepInstance si : stepInstanceRepository.findByProtocolInstanceId(pi.getId())) {
                stepActionIds.put(si.getId(), si.getActionId());
            }
        }

        List<Map<String, Object>> result = instances.stream()
                .flatMap(pi -> deviationRepository.findByProtocolInstanceId(pi.getId()).stream()
                        .map(d -> {
                            String actionId = stepActionIds.get(d.getStepInstanceId());
                            String stepName = actionId != null ? stepTitles.getOrDefault(actionId, formatActionId(actionId)) : null;
                            Map<String, Object> metadata = parseMetadata(d.getMetadata());
                            String description = buildDescription(d.getDeviationType().name(), stepName, metadata, stepTitles);
                            Map<String, Object> map = new LinkedHashMap<>();
                            map.put("deviationId", d.getId());
                            map.put("protocolInstanceId", pi.getId());
                            map.put("protocolCanonical", pi.getProtocolCanonical());
                            map.put("stepInstanceId", d.getStepInstanceId());
                            map.put("actionId", actionId);
                            map.put("stepName", stepName);
                            map.put("deviationType", d.getDeviationType());
                            map.put("detectedAt", d.getDetectedAt());
                            map.put("metadata", metadata);
                            map.put("description", description);
                            return map;
                        }))
                .filter(m -> deviationType == null ||
                        m.get("deviationType").toString().equalsIgnoreCase(deviationType))
                .filter(m -> startDate == null ||
                        !((OffsetDateTime) m.get("detectedAt")).isBefore(startDate))
                .filter(m -> endDate == null ||
                        !((OffsetDateTime) m.get("detectedAt")).isAfter(endDate))
                .sorted(Comparator.comparing(m -> ((OffsetDateTime) m.get("detectedAt")),
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private void resolveStepTitles(UUID protocolDefinitionId, Map<String, String> titles) {
        try {
            ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId).orElse(null);
            if (pd != null && pd.getDefinition() != null) {
                JsonNode root = objectMapper.readTree(pd.getDefinition());
                JsonNode actionNodes = root.get("action");
                if (actionNodes != null && actionNodes.isArray()) {
                    for (JsonNode action : actionNodes) {
                        String id = action.has("id") ? action.get("id").asText() : null;
                        String title = action.has("title") ? action.get("title").asText() : null;
                        if (id != null && title != null) {
                            titles.putIfAbsent(id, title);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse protocol definition {}: {}", protocolDefinitionId, e.getMessage());
        }
    }

    private String formatActionId(String actionId) {
        if (actionId == null) return "Unknown Step";
        return Arrays.stream(actionId.split("-"))
                .map(w -> w.substring(0, 1).toUpperCase() + w.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(actionId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(metadata, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String buildDescription(String deviationType, String stepName,
                                     Map<String, Object> metadata,
                                     Map<String, String> stepTitles) {
        String step = stepName != null ? stepName : "Unknown Step";
        switch (deviationType) {
            case "ORDER_VIOLATION":
                String completedActionId = (String) metadata.get("completedActionId");
                List<String> prereqs = metadata.get("incompletePrerequisites") instanceof List
                        ? (List<String>) metadata.get("incompletePrerequisites")
                        : List.of();
                String completedName = completedActionId != null
                        ? stepTitles.getOrDefault(completedActionId, formatActionId(completedActionId))
                        : step;
                if (!prereqs.isEmpty()) {
                    String prereqNames = prereqs.stream()
                            .map(id -> stepTitles.getOrDefault(id, formatActionId(id)))
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    return completedName + " completed before " + prereqNames;
                }
                return completedName + " completed out of order";
            case "OVERDUE":
                return step + " is overdue";
            case "MISSED":
                return step + " was missed";
            default:
                return step;
        }
    }

    private String extractResourceType(String data) {
        if (data == null) return "Unknown";
        int idx = data.indexOf("\"resourceType\"");
        if (idx < 0) return "Unknown";
        int colon = data.indexOf(':', idx);
        int quote1 = data.indexOf('"', colon + 1);
        int quote2 = data.indexOf('"', quote1 + 1);
        if (quote1 >= 0 && quote2 > quote1) return data.substring(quote1 + 1, quote2);
        return "Unknown";
    }
}
