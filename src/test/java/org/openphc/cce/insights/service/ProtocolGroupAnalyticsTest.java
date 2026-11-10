package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.StepAnalyticsDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Step analytics for a protocol with a visit, one per day, and a consultation group inside it. */
class ProtocolGroupAnalyticsTest {

    private static final UUID DEFINITION = UUID.randomUUID();
    private static final UUID V1 = UUID.randomUUID(), V2 = UUID.randomUUID(), V3 = UUID.randomUUID(),
            V4 = UUID.randomUUID(), C1 = UUID.randomUUID();

    private final StepInstanceRepository stepInstanceRepository = mock(StepInstanceRepository.class);
    private final ProtocolAnalyticsService service;

    ProtocolGroupAnalyticsTest() {
        ProtocolDefinitionRepository definitionRepository = mock(ProtocolDefinitionRepository.class);
        when(definitionRepository.findById(DEFINITION)).thenReturn(Optional.of(ProtocolDefinition.builder()
                .id(DEFINITION).url("http://x/PlanDefinition/op").version("1.0")
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
        service = new ProtocolAnalyticsService(definitionRepository, mock(ProtocolInstanceRepository.class),
                stepInstanceRepository, new ObjectMapper());
    }

    @Test
    void eachGroupCountsItsPatients_itsInstances_andTheCompletedOnes() {
        // Patient A: visit 1 has both of its own steps done but its consultation's step open, so it
        // isn't done; visit 2 is done; visit 3 has no steps, but the Matcher recorded it complete.
        // Patient B: one visit, its step still open.
        when(stepInstanceRepository.findGroupInstanceProgress(eq(DEFINITION), any(), any(), any()))
                .thenReturn(List.of(
                        row(V1, null, "visit", "A", false, 2, 2),
                        row(V2, null, "visit", "A", false, 1, 1),
                        row(V3, null, "visit", "A", true, 0, 0),
                        row(V4, null, "visit", "B", false, 1, 0),
                        row(C1, V1, "consultation", "A", false, 1, 0)));
        List<Object[]> stepRows = List.<Object[]>of(
                new Object[]{"vitals", 2L, 1L, 0L, 0L, 0L, 0L, 1L, 2L, null, null, 3L, 2L});
        when(stepInstanceRepository.findStepAnalytics(eq(DEFINITION), any(), any())).thenReturn(stepRows);

        StepAnalyticsDto analytics = service.getStepAnalytics(DEFINITION, null, null, null);

        assertThat(analytics.getGroups())
                .extracting(StepAnalyticsDto.GroupMetric::getGroupActionId, StepAnalyticsDto.GroupMetric::getTitle,
                        StepAnalyticsDto.GroupMetric::getPatientCount, StepAnalyticsDto.GroupMetric::getInstanceCount,
                        StepAnalyticsDto.GroupMetric::getCompletedInstanceCount, StepAnalyticsDto.GroupMetric::getCompletionRate)
                .containsExactly(
                        tuple("visit", "Outpatient Visit", 2L, 4L, 2L, 0.5),
                        tuple("consultation", "Clinical Consultation", 1L, 1L, 0L, 0.0));
        // Patients, and step instances: one per visit.
        assertThat(analytics.getSteps()).singleElement().satisfies(vitals -> {
            assertThat(vitals.getTotalInstances()).isEqualTo(2);
            assertThat(vitals.getCompletedCount()).isEqualTo(1);
            assertThat(vitals.getStepInstanceCount()).isEqualTo(3);
            assertThat(vitals.getCompletedStepInstanceCount()).isEqualTo(2);
        });
    }

    @Test
    void aGroupWithNoInstancesInScope_isListedWithZeros() {
        when(stepInstanceRepository.findGroupInstanceProgress(eq(DEFINITION), any(), any(), any()))
                .thenReturn(List.of());
        when(stepInstanceRepository.findStepAnalytics(eq(DEFINITION), any(), any())).thenReturn(List.of());

        assertThat(service.getStepAnalytics(DEFINITION, null, null, null).getGroups())
                .extracting(StepAnalyticsDto.GroupMetric::getGroupActionId, StepAnalyticsDto.GroupMetric::getInstanceCount)
                .containsExactly(tuple("visit", 0L), tuple("consultation", 0L));
    }

    private static Object[] row(UUID id, UUID parent, String group, String patient, boolean recorded,
                                long steps, long done) {
        return new Object[]{id, parent, group, patient, recorded, steps, done};
    }
}
