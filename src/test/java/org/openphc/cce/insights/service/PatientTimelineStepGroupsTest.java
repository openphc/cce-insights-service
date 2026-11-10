package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepGroupInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.DeviationType;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;
import org.openphc.cce.insights.domain.enums.SlaStatus;
import org.openphc.cce.insights.domain.enums.StepStatus;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.MatcherEventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepGroupInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.PatientTimelineDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step groups in the patient's Protocol Journey. The protocol:
 * <pre>
 * registration
 * visit (step group, one per day)
 *   history
 *   bp
 *   lab (step group inside the visit)
 *     lab-order
 * </pre>
 */
class PatientTimelineStepGroupsTest {

    private static final String PATIENT_ID = "260227-1651-7600";
    private static final UUID PROTOCOL_INSTANCE_ID = UUID.randomUUID();
    private static final UUID PROTOCOL_DEFINITION_ID = UUID.randomUUID();
    private static final OffsetDateTime DAY_1 = OffsetDateTime.parse("2026-11-02T09:00:00Z");
    private static final OffsetDateTime DAY_2 = OffsetDateTime.parse("2026-11-03T09:00:00Z");

    private static final String PROTOCOL = """
            { "action": [
              { "id": "registration", "title": "Registration" },
              { "id": "visit", "title": "Visit", "groupingBehavior": "logical-group",
                "extension": [{ "url": "http://openphc.org/fhir/StructureDefinition/cycle-boundary",
                                "valueCode": "calendar-day" }],
                "action": [
                  { "id": "history", "title": "History", "requiredBehavior": "must" },
                  { "id": "bp", "title": "Blood Pressure" },
                  { "id": "lab", "title": "Lab", "groupingBehavior": "logical-group",
                    "action": [{ "id": "lab-order", "title": "Lab Order" }] }
                ] }
            ] }
            """;

    private final List<StepInstance> stepInstances = new ArrayList<>();
    private final List<StepGroupInstance> groupInstances = new ArrayList<>();
    private final List<Deviation> deviations = new ArrayList<>();
    private PatientTimelineService service;

    @BeforeEach
    void setUp() {
        ProtocolInstanceRepository protocolInstanceRepository = mock(ProtocolInstanceRepository.class);
        StepInstanceRepository stepInstanceRepository = mock(StepInstanceRepository.class);
        StepGroupInstanceRepository stepGroupInstanceRepository = mock(StepGroupInstanceRepository.class);
        ProtocolDefinitionRepository protocolDefinitionRepository = mock(ProtocolDefinitionRepository.class);
        DeviationRepository deviationRepository = mock(DeviationRepository.class);
        DailyKpiRepository dailyKpiRepository = mock(DailyKpiRepository.class);
        service = new PatientTimelineService(protocolInstanceRepository, stepInstanceRepository,
                stepGroupInstanceRepository, protocolDefinitionRepository, deviationRepository,
                mock(MatcherEventLogRepository.class), dailyKpiRepository, new ObjectMapper());

        when(protocolInstanceRepository.findByPatientId(PATIENT_ID)).thenReturn(List.of(ProtocolInstance.builder()
                .id(PROTOCOL_INSTANCE_ID)
                .protocolDefinitionId(PROTOCOL_DEFINITION_ID)
                .patientId(PATIENT_ID)
                .protocolCanonical("http://openphc.org/fhir/PlanDefinition/visits|1.0")
                .status(ProtocolInstanceStatus.ACTIVE)
                .enrolledAt(DAY_1)
                .build()));
        when(protocolDefinitionRepository.findById(PROTOCOL_DEFINITION_ID)).thenReturn(Optional.of(
                ProtocolDefinition.builder().id(PROTOCOL_DEFINITION_ID).definition(PROTOCOL).build()));
        when(stepInstanceRepository.findByProtocolInstanceIdIn(any())).thenReturn(stepInstances);
        when(stepGroupInstanceRepository.findByProtocolInstanceIdIn(any())).thenReturn(groupInstances);
        when(deviationRepository.findByProtocolInstanceIdIn(any())).thenReturn(deviations);
        when(dailyKpiRepository.getFacilityReference()).thenReturn(List.of());
    }

    @Test
    void aGroupThatNeverOpened_isNotStarted_withNoStepStatus() {
        PatientTimelineDto.JourneyStep visit = journeyRow("visit");

        assertThat(visit.isGroup()).isTrue();
        assertThat(visit.getStatus()).isEqualTo("NOT_STARTED");
        assertThat(visit.getStepStatus()).isNull();
        assertThat(visit.getGroupInstances()).isEmpty();
        assertThat(journeyRow("history").isGroup()).isFalse();
    }

    @Test
    void eachVisit_isListedInOpeningOrder_withHowManyOfItsStepsAreDone() {
        StepGroupInstance secondVisit = groupInstance("visit", 1, DAY_2, null);
        StepGroupInstance firstVisit = groupInstance("visit", 0, DAY_1, null);
        step("history", firstVisit, DAY_1.plusHours(1));
        step("bp", firstVisit, DAY_1.plusHours(2));
        step("history", secondVisit, DAY_2.plusHours(1));
        step("bp", secondVisit, null);

        PatientTimelineDto.JourneyStep visit = journeyRow("visit");

        assertThat(visit.getGroupInstances()).extracting(PatientTimelineDto.GroupInstance::getCycleIndex)
                .containsExactly(0, 1);
        PatientTimelineDto.GroupInstance first = visit.getGroupInstances().get(0);
        assertThat(first.getStatus()).isEqualTo("COMPLETED");
        assertThat(first.getCompletedSteps()).isEqualTo(2);
        assertThat(first.getTotalSteps()).isEqualTo(2);
        assertThat(first.getCompletedAt()).isEqualTo(DAY_1.plusHours(2));
        PatientTimelineDto.GroupInstance second = visit.getGroupInstances().get(1);
        assertThat(second.getStatus()).isEqualTo("NOT_STARTED");
        assertThat(second.getCompletedSteps()).isEqualTo(1);
        assertThat(second.getTotalSteps()).isEqualTo(2);
        assertThat(second.getCompletedAt()).isNull();
        // The row takes its best instance's status, as a step's row does.
        assertThat(visit.getStatus()).isEqualTo("COMPLETED");
        assertThat(visit.getCompletionCount()).isEqualTo(1);
    }

    @Test
    void aVisitWithAStepStillAwaited_isOpen() {
        StepGroupInstance firstVisit = groupInstance("visit", 0, DAY_1, null);
        step("history", firstVisit, null);

        PatientTimelineDto.JourneyStep visit = journeyRow("visit");

        assertThat(visit.getStatus()).isEqualTo("NOT_STARTED");
        assertThat(visit.getStepStatus()).isEqualTo("NOT_STARTED");
        assertThat(visit.getCompletionCount()).isZero();
    }

    @Test
    void aVisitWithNoStepsYet_isOpen() {
        groupInstance("visit", 0, DAY_1, null);

        assertThat(journeyRow("visit").getStatus()).isEqualTo("NOT_STARTED");
    }

    @Test
    void aNestedGroupsSteps_countForItsParentToo() {
        StepGroupInstance firstVisit = groupInstance("visit", 0, DAY_1, null);
        StepGroupInstance lab = groupInstance("lab", 0, DAY_1.plusHours(1), firstVisit);
        step("history", firstVisit, DAY_1.plusHours(1));
        step("lab-order", lab, null);

        PatientTimelineDto.GroupInstance visit = journeyRow("visit").getGroupInstances().get(0);
        assertThat(visit.getTotalSteps()).isEqualTo(2);
        assertThat(visit.getCompletedSteps()).isEqualTo(1);
        assertThat(visit.getStatus()).isEqualTo("NOT_STARTED");

        PatientTimelineDto.JourneyStep labRow = journeyRow("lab");
        assertThat(labRow.getDepth()).isEqualTo(1);
        assertThat(labRow.getParentActionId()).isEqualTo("visit");
        assertThat(labRow.getGroupInstances()).singleElement().satisfies(labInstance -> {
            assertThat(labInstance.getParentGroupInstanceId()).isEqualTo(firstVisit.getId().toString());
            assertThat(labInstance.getTotalSteps()).isEqualTo(1);
        });
    }

    @Test
    void aMissedGroupDeadline_showsWhileTheGroupIsOpen() {
        StepGroupInstance firstVisit = StepGroupInstance.builder()
                .id(UUID.randomUUID()).protocolInstanceId(PROTOCOL_INSTANCE_ID).groupActionId("visit")
                .openedAt(DAY_1).dueDate(DAY_1).slaStatus(SlaStatus.MISSED).build();
        groupInstances.add(firstVisit);
        step("history", firstVisit, null);

        PatientTimelineDto.JourneyStep visit = journeyRow("visit");

        assertThat(visit.getStatus()).isEqualTo("MISSED");
        assertThat(visit.getSlaStatus()).isEqualTo("MISSED");
        assertThat(visit.getDueDate()).isEqualTo(DAY_1.toString());
    }

    @Test
    void aCompletionTheMatcherRecorded_counts_evenWithAnOptionalStepLeft() {
        StepGroupInstance firstVisit = StepGroupInstance.builder()
                .id(UUID.randomUUID()).protocolInstanceId(PROTOCOL_INSTANCE_ID).groupActionId("visit")
                .openedAt(DAY_1).completedAt(DAY_1.plusHours(3)).build();
        groupInstances.add(firstVisit);
        step("bp", firstVisit, null);

        PatientTimelineDto.GroupInstance visit = journeyRow("visit").getGroupInstances().get(0);

        assertThat(visit.getStatus()).isEqualTo("COMPLETED");
        assertThat(visit.getCompletedAt()).isEqualTo(DAY_1.plusHours(3));
    }

    @Test
    void aStepRow_listsEachOfItsStepInstances_withTheVisitItIsIn() {
        StepGroupInstance firstVisit = groupInstance("visit", 0, DAY_1, null);
        StepGroupInstance secondVisit = groupInstance("visit", 1, DAY_2, null);
        step("history", firstVisit, DAY_1.plusHours(1));
        step("history", secondVisit, null);
        step("registration", null, DAY_1);

        PatientTimelineDto.JourneyStep history = journeyRow("history");

        // The row still merges both visits; its instances tell them apart.
        assertThat(history.getCompletionCount()).isEqualTo(1);
        assertThat(history.getInstances()).hasSize(2);
        assertThat(history.getInstances())
                .filteredOn(i -> firstVisit.getId().toString().equals(i.getGroupInstanceId()))
                .singleElement().satisfies(i -> {
                    assertThat(i.getStatus()).isEqualTo("COMPLETED");
                    assertThat(i.getStepStatus()).isEqualTo("COMPLETED");
                });
        assertThat(history.getInstances())
                .filteredOn(i -> secondVisit.getId().toString().equals(i.getGroupInstanceId()))
                .singleElement().satisfies(i -> {
                    assertThat(i.getStatus()).isEqualTo("NOT_STARTED");
                    assertThat(i.getStepStatus()).isEqualTo("NOT_STARTED");
                });
        assertThat(journeyRow("registration").getInstances()).singleElement()
                .satisfies(i -> assertThat(i.getGroupInstanceId()).isNull());
        assertThat(journeyRow("bp").getInstances()).isEmpty();
        assertThat(journeyRow("visit").getInstances()).isNull();
    }

    @Test
    void aStepInstancesDeviation_isDescribedOnThatInstanceOnly() {
        StepGroupInstance firstVisit = groupInstance("visit", 0, DAY_1, null);
        StepGroupInstance secondVisit = groupInstance("visit", 1, DAY_2, null);
        step("history", firstVisit, DAY_1.plusHours(1));
        step("history", secondVisit, null);
        StepInstance missed = stepInstances.get(1);
        deviations.add(Deviation.builder()
                .id(UUID.randomUUID())
                .protocolInstanceId(PROTOCOL_INSTANCE_ID)
                .stepInstanceId(missed.getId())
                .deviationType(DeviationType.MISSED)
                .build());

        List<PatientTimelineDto.JourneyStepInstance> instances = journeyRow("history").getInstances();

        assertThat(instances).filteredOn(i -> i.getStepInstanceId().equals(missed.getId().toString()))
                .singleElement()
                .satisfies(i -> assertThat(i.getDescription()).isEqualTo("Step missed — no completion recorded within window"));
        assertThat(instances).filteredOn(i -> !i.getStepInstanceId().equals(missed.getId().toString()))
                .singleElement()
                .satisfies(i -> assertThat(i.getDescription()).isNull());
    }

    @Test
    void timelineEvents_sayWhichGroupAndCycleTheirStepIsIn() {
        StepGroupInstance secondVisit = groupInstance("visit", 1, DAY_2, null);
        step("registration", null, DAY_1);
        step("history", secondVisit, DAY_2.plusHours(1));

        List<PatientTimelineDto.TimelineEvent> events = protocol().getTimeline();

        PatientTimelineDto.TimelineEvent history = event(events, "history");
        assertThat(history.getGroupActionId()).isEqualTo("visit");
        assertThat(history.getCycleIndex()).isEqualTo(1);
        PatientTimelineDto.TimelineEvent registration = event(events, "registration");
        assertThat(registration.getGroupActionId()).isNull();
        assertThat(registration.getCycleIndex()).isNull();
    }

    private StepGroupInstance groupInstance(String groupActionId, int cycleIndex, OffsetDateTime openedAt,
                                            StepGroupInstance parent) {
        StepGroupInstance groupInstance = StepGroupInstance.builder()
                .id(UUID.randomUUID())
                .protocolInstanceId(PROTOCOL_INSTANCE_ID)
                .parentGroupInstanceId(parent != null ? parent.getId() : null)
                .groupActionId(groupActionId)
                .cycleIndex(cycleIndex)
                .openedAt(openedAt)
                .build();
        groupInstances.add(groupInstance);
        return groupInstance;
    }

    /** A step instance, done at {@code completedAt}, or still awaited when it is null. */
    private void step(String actionId, StepGroupInstance groupInstance, OffsetDateTime completedAt) {
        stepInstances.add(StepInstance.builder()
                .id(UUID.randomUUID())
                .protocolInstanceId(PROTOCOL_INSTANCE_ID)
                .actionId(actionId)
                .stepGroupInstanceId(groupInstance != null ? groupInstance.getId() : null)
                .stepStatus(completedAt != null ? StepStatus.COMPLETED : StepStatus.NOT_STARTED)
                .completedAt(completedAt)
                .build());
    }

    private PatientTimelineDto.ProtocolTimeline protocol() {
        return service.getTimeline(PATIENT_ID, null, null).getProtocols().get(0);
    }

    private PatientTimelineDto.JourneyStep journeyRow(String actionId) {
        return protocol().getJourney().stream()
                .filter(row -> row.getActionId().equals(actionId))
                .findFirst()
                .orElseThrow();
    }

    private static PatientTimelineDto.TimelineEvent event(List<PatientTimelineDto.TimelineEvent> events,
                                                          String actionId) {
        return events.stream().filter(e -> actionId.equals(e.getActionId())).findFirst().orElseThrow();
    }
}
