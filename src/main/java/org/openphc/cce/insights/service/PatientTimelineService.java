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

            // Resolve step titles from protocol definition
            Map<String, String> stepTitles = resolveStepTitles(pi.getProtocolDefinitionId());

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
                                .state(si.getState().name());

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
                    .timeline(events)
                    .build());
        }

        return PatientTimelineDto.builder()
                .patientId(patientId)
                .protocols(protocols)
                .build();
    }

    private Map<String, String> resolveStepTitles(UUID protocolDefinitionId) {
        Map<String, String> titles = new HashMap<>();
        if (protocolDefinitionId == null) return titles;
        try {
            ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId).orElse(null);
            if (pd != null && pd.getDefinition() != null) {
                JsonNode root = objectMapper.readTree(pd.getDefinition());
                JsonNode actions = root.get("action");
                if (actions != null && actions.isArray()) {
                    for (JsonNode action : actions) {
                        String id = action.has("id") ? action.get("id").asText() : null;
                        String title = action.has("title") ? action.get("title").asText() : null;
                        if (id != null && title != null) {
                            titles.put(id, title);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse protocol definition {}: {}", protocolDefinitionId, e.getMessage());
        }
        return titles;
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
