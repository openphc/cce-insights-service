package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.*;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientTimelineService {

    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final DeviationRepository deviationRepository;
    private final EventLogRepository eventLogRepository;

    public PatientTimelineDto getTimeline(String patientId) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findByPatientId(patientId);

        List<PatientTimelineDto.ProtocolTimeline> protocols = new ArrayList<>();
        for (ProtocolInstance pi : instances) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceIdOrderByDueDateAsc(pi.getId());
            long completed = steps.stream()
                    .filter(s -> s.getState() == StepState.COMPLETED || s.getState() == StepState.SKIPPED)
                    .count();
            double rate = steps.isEmpty() ? 0.0 : (double) completed / steps.size();

            List<PatientTimelineDto.TimelineEvent> events = new ArrayList<>();

            events.add(PatientTimelineDto.TimelineEvent.builder()
                    .timestamp(pi.getEnrolledAt())
                    .type("enrollment")
                    .description("Enrolled in " + pi.getProtocolCanonical())
                    .build());

            for (StepInstance si : steps) {
                if (si.getState() == StepState.COMPLETED && si.getCompletedAt() != null) {
                    events.add(PatientTimelineDto.TimelineEvent.builder()
                            .timestamp(si.getCompletedAt())
                            .type("step_completed")
                            .actionId(si.getActionId())
                            .completionStatus(si.getCompletionStatus() != null ?
                                    si.getCompletionStatus().name().toLowerCase() : null)
                            .source(si.getCompletedBySource())
                            .build());
                } else if (si.getState() == StepState.OVERDUE && si.getOverdueDate() != null) {
                    int daysOverdue = si.getDueDate() != null ?
                            (int) ChronoUnit.DAYS.between(si.getDueDate(), java.time.OffsetDateTime.now()) : 0;
                    events.add(PatientTimelineDto.TimelineEvent.builder()
                            .timestamp(si.getOverdueDate())
                            .type("step_overdue")
                            .actionId(si.getActionId())
                            .daysOverdue(Math.max(daysOverdue, 0))
                            .build());
                } else if (si.getState() == StepState.MISSED && si.getMissedDate() != null) {
                    events.add(PatientTimelineDto.TimelineEvent.builder()
                            .timestamp(si.getMissedDate())
                            .type("step_missed")
                            .actionId(si.getActionId())
                            .build());
                }
            }

            events.sort(Comparator.comparing(PatientTimelineDto.TimelineEvent::getTimestamp));

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
}
