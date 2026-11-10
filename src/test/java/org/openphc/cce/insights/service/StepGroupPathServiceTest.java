package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepGroupInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.StepStatus;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.StepGroupInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.GroupPathEntryDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which visit a step instance is in. The protocol has a visit, one per day, with a consultation group
 * inside it. The patient came twice, and had two consultations in the first visit.
 */
class StepGroupPathServiceTest {

    private static final UUID ENROLLMENT = UUID.randomUUID();
    private static final UUID DEFINITION = UUID.randomUUID();
    private static final OffsetDateTime DAY_1 = OffsetDateTime.parse("2026-09-05T08:00:00Z");
    private static final OffsetDateTime DAY_2 = OffsetDateTime.parse("2026-09-06T08:00:00Z");

    private final List<StepGroupInstance> groupInstances = new ArrayList<>();
    private final List<StepInstance> stepInstances = new ArrayList<>();
    private final StepGroupPathService service;

    StepGroupPathServiceTest() {
        StepInstanceRepository stepInstanceRepository = mock(StepInstanceRepository.class);
        StepGroupInstanceRepository groupInstanceRepository = mock(StepGroupInstanceRepository.class);
        ProtocolDefinitionRepository definitionRepository = mock(ProtocolDefinitionRepository.class);
        when(stepInstanceRepository.findByProtocolInstanceIdIn(any())).thenReturn(stepInstances);
        when(groupInstanceRepository.findByProtocolInstanceIdIn(any())).thenReturn(groupInstances);
        when(definitionRepository.findById(DEFINITION)).thenReturn(Optional.of(ProtocolDefinition.builder()
                .id(DEFINITION)
                .definition("""
                        { "action": [
                          { "id": "registration", "title": "Registration" },
                          { "id": "visit", "title": "Outpatient Visit", "groupingBehavior": "logical-group",
                            "action": [
                              { "id": "vitals", "title": "Vitals" },
                              { "id": "consultation", "title": "Clinical Consultation", "groupingBehavior": "logical-group",
                                "action": [{ "id": "diagnosis", "title": "Diagnosis" }] }
                            ] }
                        ] }
                        """)
                .build()));
        service = new StepGroupPathService(stepInstanceRepository, groupInstanceRepository, definitionRepository,
                new ObjectMapper());
    }

    @Test
    void aStepSaysWhichVisit_andWhichConsultationInsideIt_itIsIn() {
        StepGroupInstance visit2 = groupInstance("visit", null, 1, DAY_2);
        StepGroupInstance visit1 = groupInstance("visit", null, 0, DAY_1);
        StepGroupInstance consultation1 = groupInstance("consultation", visit1, 0, DAY_1.plusHours(1));
        StepGroupInstance consultation2 = groupInstance("consultation", visit1, 1, DAY_1.plusHours(3));
        StepGroupInstance consultationInVisit2 = groupInstance("consultation", visit2, 0, DAY_2.plusHours(1));
        UUID registration = stepInstance("registration", null);
        UUID vitalsInVisit2 = stepInstance("vitals", visit2);
        UUID secondDiagnosis = stepInstance("diagnosis", consultation2);
        UUID diagnosisInVisit2 = stepInstance("diagnosis", consultationInVisit2);
        stepInstance("diagnosis", consultation1);

        Map<UUID, List<GroupPathEntryDto>> paths = service.pathsOf(List.of(enrollment()));

        assertThat(paths).doesNotContainKey(registration);
        assertThat(paths.get(vitalsInVisit2))
                .extracting(GroupPathEntryDto::getGroupName, GroupPathEntryDto::getNumber, GroupPathEntryDto::getOpenedAt)
                .containsExactly(tuple("Outpatient Visit", 2, DAY_2));
        // Numbered inside each visit: visit 1 had two consultations, visit 2 had one.
        assertThat(paths.get(secondDiagnosis))
                .extracting(GroupPathEntryDto::getGroupName, GroupPathEntryDto::getNumber)
                .containsExactly(tuple("Outpatient Visit", 1), tuple("Clinical Consultation", 2));
        assertThat(paths.get(diagnosisInVisit2))
                .extracting(GroupPathEntryDto::getGroupName, GroupPathEntryDto::getNumber)
                .containsExactly(tuple("Outpatient Visit", 2), tuple("Clinical Consultation", null));
        assertThat(paths.get(secondDiagnosis).get(1).getGroupInstanceId()).isEqualTo(consultation2.getId().toString());
    }

    @Test
    void aGroupThatOpenedOnce_hasNoNumber() {
        StepGroupInstance visit = groupInstance("visit", null, 0, DAY_1);
        UUID vitals = stepInstance("vitals", visit);

        assertThat(service.pathsOf(List.of(enrollment())).get(vitals))
                .singleElement().satisfies(entry -> assertThat(entry.getNumber()).isNull());
    }

    @Test
    void anEnrollmentWithNoGroups_hasNoPaths() {
        stepInstance("registration", null);

        assertThat(service.pathsOf(List.of(enrollment()))).isEmpty();
    }

    private ProtocolInstance enrollment() {
        return ProtocolInstance.builder().id(ENROLLMENT).protocolDefinitionId(DEFINITION).build();
    }

    private StepGroupInstance groupInstance(String groupActionId, StepGroupInstance parent, int cycleIndex,
                                            OffsetDateTime openedAt) {
        StepGroupInstance groupInstance = StepGroupInstance.builder()
                .id(UUID.randomUUID())
                .protocolInstanceId(ENROLLMENT)
                .parentGroupInstanceId(parent != null ? parent.getId() : null)
                .groupActionId(groupActionId)
                .cycleIndex(cycleIndex)
                .openedAt(openedAt)
                .build();
        groupInstances.add(groupInstance);
        return groupInstance;
    }

    private UUID stepInstance(String actionId, StepGroupInstance groupInstance) {
        StepInstance stepInstance = StepInstance.builder()
                .id(UUID.randomUUID())
                .protocolInstanceId(ENROLLMENT)
                .actionId(actionId)
                .stepGroupInstanceId(groupInstance != null ? groupInstance.getId() : null)
                .stepStatus(StepStatus.NOT_STARTED)
                .build();
        stepInstances.add(stepInstance);
        return stepInstance.getId();
    }
}
