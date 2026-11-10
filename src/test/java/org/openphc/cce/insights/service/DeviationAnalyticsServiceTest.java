package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.web.dto.DeviationDto;
import org.openphc.cce.insights.web.dto.GroupPathEntryDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The deviation list says which visit each deviation's step is in. The repositories are mocked. */
class DeviationAnalyticsServiceTest {

    private final DeviationRepository deviationRepository = mock(DeviationRepository.class);
    private final ProtocolInstanceRepository protocolInstanceRepository = mock(ProtocolInstanceRepository.class);
    private final StepGroupPathService stepGroupPathService = mock(StepGroupPathService.class);
    private final DeviationAnalyticsService service = new DeviationAnalyticsService(deviationRepository,
            protocolInstanceRepository, stepGroupPathService);

    @Test
    void getDeviations_saysWhichVisitEachDeviationsStepIsIn() {
        UUID enrollment = UUID.randomUUID();
        UUID inAVisit = UUID.randomUUID();
        UUID inNoGroup = UUID.randomUUID();
        when(deviationRepository.findFilteredDeviations(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new Object[]{UUID.randomUUID(), "P-1", enrollment, "c|1", inAVisit, "vitals",
                                "MISSED", null, "FAC-1", null},
                        new Object[]{UUID.randomUUID(), "P-1", enrollment, "c|1", inNoGroup, "registration",
                                "OVERDUE", null, "FAC-1", null}));
        ProtocolInstance protocolInstance = ProtocolInstance.builder().id(enrollment).build();
        when(protocolInstanceRepository.findAllById(List.of(enrollment))).thenReturn(List.of(protocolInstance));
        GroupPathEntryDto visit2 = GroupPathEntryDto.builder()
                .groupActionId("visit").groupName("Outpatient Visit").number(2).build();
        when(stepGroupPathService.pathsOf(List.of(protocolInstance))).thenReturn(Map.of(inAVisit, List.of(visit2)));

        List<DeviationDto> deviations = service.getDeviations(null, null, null, null, null, 50);

        assertThat(deviations.get(0).getGroupPath()).containsExactly(visit2);
        assertThat(deviations.get(1).getGroupPath()).isEmpty();
    }
}
