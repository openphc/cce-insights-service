package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.entity.MatcherEventLog;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.insights.domain.enums.SlaStatus;
import org.openphc.cce.insights.domain.enums.StepStatus;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.MatcherEventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A step's time on the patient timeline is its completed_at (the clinical time Matcher judged it by),
 * never a time read back out of the completing event's FHIR payload.
 */
class PatientTimelineCompletionTimeTest {

    private static final String PATIENT = "patient-1";
    private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.of(2026, 11, 2, 10, 0, 0, 0, ZoneOffset.UTC);

    private final ProtocolInstanceRepository protocolInstances = mock(ProtocolInstanceRepository.class);
    private final StepInstanceRepository stepInstances = mock(StepInstanceRepository.class);
    private final ProtocolDefinitionRepository protocolDefinitions = mock(ProtocolDefinitionRepository.class);
    private final DeviationRepository deviations = mock(DeviationRepository.class);
    private final MatcherEventLogRepository eventLogs = mock(MatcherEventLogRepository.class);

    private final PatientTimelineService service = new PatientTimelineService(
            protocolInstances, stepInstances, protocolDefinitions, deviations, eventLogs, new ObjectMapper());

    private final UUID definitionId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(protocolInstances.findByPatientId(PATIENT)).thenReturn(List.of(ProtocolInstance.builder()
                .id(instanceId).protocolDefinitionId(definitionId).patientId(PATIENT)
                .protocolCanonical("http://example.org/protocol").status(ProtocolInstanceStatus.ACTIVE)
                .build()));
        when(protocolDefinitions.findById(definitionId)).thenReturn(Optional.of(ProtocolDefinition.builder()
                .id(definitionId)
                .definition("{\"action\":[{\"id\":\"diagnosis\",\"title\":\"Diagnosis\"},"
                        + "{\"id\":\"follow-up\",\"title\":\"Follow-up\"}]}")
                .build()));
        when(deviations.findByProtocolInstanceIdIn(anyList())).thenReturn(List.of());
        // The completing event's payload carries a different time (and Condition has no
        // effectiveDateTime at all): the timeline must not show it.
        when(eventLogs.findByMatcherEventIds(any())).thenReturn(List.of(MatcherEventLog.builder()
                .id(eventId)
                .data("{\"resourceType\":\"Condition\",\"recordedDate\":\"2026-11-02T10:00:00Z\","
                        + "\"meta\":{\"lastUpdated\":\"2026-11-05T08:00:00Z\"}}")
                .build()));
        when(stepInstances.findByProtocolInstanceIdIn(anyList())).thenReturn(List.of(
                StepInstance.builder().id(UUID.randomUUID()).protocolInstanceId(instanceId)
                        .actionId("diagnosis").stepStatus(StepStatus.COMPLETED).slaStatus(SlaStatus.MET)
                        .completedAt(COMPLETED_AT).matchedEventId(eventId).build(),
                StepInstance.builder().id(UUID.randomUUID()).protocolInstanceId(instanceId)
                        .actionId("follow-up").stepStatus(StepStatus.NOT_STARTED)
                        .dueDate(COMPLETED_AT.plusDays(7)).build()));
    }

    @Test
    void aCompletedStepsTime_isItsCompletedAt_onTheJourneyRowAndTheTimeline() {
        PatientTimelineDto.ProtocolTimeline protocol = service.getTimeline(PATIENT).getProtocols().get(0);

        assertThat(protocol.getJourney())
                .filteredOn(row -> "diagnosis".equals(row.getActionId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getEffectiveDateTime()).isEqualTo(COMPLETED_AT.toString()));
        assertThat(protocol.getTimeline())
                .filteredOn(e -> "diagnosis".equals(e.getActionId()))
                .singleElement()
                .satisfies(e -> assertThat(e.getEffectiveDateTime()).isEqualTo(COMPLETED_AT.toString()));
    }

    @Test
    void aStepNotCompleted_hasNoTime() {
        PatientTimelineDto.ProtocolTimeline protocol = service.getTimeline(PATIENT).getProtocols().get(0);

        assertThat(protocol.getJourney())
                .filteredOn(row -> "follow-up".equals(row.getActionId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getEffectiveDateTime()).isNull());
        assertThat(protocol.getTimeline())
                .filteredOn(e -> "follow-up".equals(e.getActionId()))
                .singleElement()
                .satisfies(e -> assertThat(e.getEffectiveDateTime()).isNull());
    }
}
