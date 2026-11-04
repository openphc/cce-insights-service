package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.ActionOrderEntryDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtocolActionOrderTest {

    @Test
    void aStepGroupIsMarkedAsAGroup_andItsStepsKeepItAsTheirParent() {
        UUID protocolDefinitionId = UUID.randomUUID();
        ProtocolDefinitionRepository protocolDefinitionRepository = mock(ProtocolDefinitionRepository.class);
        when(protocolDefinitionRepository.findById(protocolDefinitionId)).thenReturn(Optional.of(
                ProtocolDefinition.builder().id(protocolDefinitionId).definition("""
                        { "action": [
                          { "id": "registration", "title": "Registration" },
                          { "id": "visit", "title": "Visit", "groupingBehavior": "logical-group",
                            "action": [{ "id": "history", "title": "History" }] }
                        ] }
                        """).build()));
        ProtocolAnalyticsService service = new ProtocolAnalyticsService(protocolDefinitionRepository,
                mock(ProtocolInstanceRepository.class), mock(StepInstanceRepository.class), new ObjectMapper());

        List<ActionOrderEntryDto> order = service.getActionOrder(protocolDefinitionId);

        assertThat(order).extracting(ActionOrderEntryDto::getActionId, ActionOrderEntryDto::isGroup,
                        ActionOrderEntryDto::getParentActionId)
                .containsExactly(
                        tuple("registration", false, null),
                        tuple("visit", true, null),
                        tuple("history", false, "visit"));
    }
}
