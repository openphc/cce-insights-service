package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepGroupInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.StepGroupInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.GroupPathEntryDto;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which group instances a step instance is in, such as "Outpatient Visit #2 › Clinical Consultation",
 * so a deviation or a step can say which visit it belongs to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StepGroupPathService {

    private final StepInstanceRepository stepInstanceRepository;
    private final StepGroupInstanceRepository stepGroupInstanceRepository;
    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ObjectMapper objectMapper;

    /**
     * The group path of every step instance of these enrollments that is in a group, by step instance
     * id. A step instance in no group has no entry.
     */
    public Map<UUID, List<GroupPathEntryDto>> pathsOf(Collection<ProtocolInstance> protocolInstances) {
        if (protocolInstances.isEmpty()) return Map.of();
        List<UUID> ids = protocolInstances.stream().map(ProtocolInstance::getId).distinct().toList();
        Map<UUID, List<StepGroupInstance>> groupInstancesByEnrollment = stepGroupInstanceRepository
                .findByProtocolInstanceIdIn(ids).stream()
                .collect(Collectors.groupingBy(StepGroupInstance::getProtocolInstanceId));
        if (groupInstancesByEnrollment.isEmpty()) return Map.of(); // no groups opened: no paths
        Map<UUID, List<StepInstance>> stepInstancesByEnrollment = stepInstanceRepository
                .findByProtocolInstanceIdIn(ids).stream()
                .collect(Collectors.groupingBy(StepInstance::getProtocolInstanceId));

        Map<UUID, Map<String, String>> titlesByDefinition = new HashMap<>();
        Map<UUID, List<GroupPathEntryDto>> paths = new HashMap<>();
        for (ProtocolInstance protocolInstance : protocolInstances) {
            List<StepGroupInstance> groupInstances = groupInstancesByEnrollment.get(protocolInstance.getId());
            if (groupInstances == null) continue;
            Map<String, String> titles = protocolInstance.getProtocolDefinitionId() == null ? Map.of()
                    : titlesByDefinition.computeIfAbsent(protocolInstance.getProtocolDefinitionId(), this::actionTitles);
            List<StepInstance> stepInstances = stepInstancesByEnrollment.getOrDefault(protocolInstance.getId(), List.of());
            StepGroupProgress progress = new StepGroupProgress(groupInstances, stepInstances);
            for (StepInstance stepInstance : stepInstances) {
                List<GroupPathEntryDto> path = progress.groupInstancesAround(stepInstance).stream()
                        .map(groupInstance -> GroupPathEntryDto.builder()
                                .groupActionId(groupInstance.getGroupActionId())
                                .groupName(titles.getOrDefault(groupInstance.getGroupActionId(),
                                        groupInstance.getGroupActionId()))
                                .groupInstanceId(groupInstance.getId().toString())
                                .number(progress.numberOf(groupInstance))
                                .openedAt(groupInstance.getOpenedAt())
                                .build())
                        .toList();
                if (!path.isEmpty()) paths.put(stepInstance.getId(), path);
            }
        }
        return paths;
    }

    /** Every action's title in a protocol, groups and nested actions included. */
    private Map<String, String> actionTitles(UUID protocolDefinitionId) {
        Map<String, String> titles = new HashMap<>();
        try {
            ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId).orElse(null);
            if (pd != null && pd.getDefinition() != null) {
                collectTitles(objectMapper.readTree(pd.getDefinition()).get("action"), titles);
            }
        } catch (Exception e) {
            log.warn("Failed to parse protocol definition {}: {}", protocolDefinitionId, e.getMessage());
        }
        return titles;
    }

    private static void collectTitles(JsonNode actions, Map<String, String> titles) {
        if (actions == null || !actions.isArray()) return;
        for (JsonNode action : actions) {
            String id = action.hasNonNull("id") ? action.get("id").asText() : null;
            String title = action.hasNonNull("title") ? action.get("title").asText() : null;
            if (id != null && title != null) titles.putIfAbsent(id, title);
            collectTitles(action.get("action"), titles);
        }
    }

    /** The path for one step instance, or an empty list. */
    public static List<GroupPathEntryDto> pathOf(Map<UUID, List<GroupPathEntryDto>> paths, UUID stepInstanceId) {
        return stepInstanceId == null ? List.of() : Objects.requireNonNullElse(paths.get(stepInstanceId), List.of());
    }
}
