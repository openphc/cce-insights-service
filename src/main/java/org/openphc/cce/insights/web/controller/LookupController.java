package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/v1/insights/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final ComplianceEventLogRepository complianceEventLogRepository;
    private final InboundEventRepository inboundEventRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/protocols")
    @Cacheable(value = "lookups", key = "'protocols'")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProtocols() {
        List<ProtocolDefinition> protocols = protocolDefinitionRepository.findAll();
        List<Map<String, Object>> result = protocols.stream().map(pd -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", pd.getId());
            map.put("url", pd.getUrl());
            map.put("version", pd.getVersion());
            map.put("canonical", pd.getUrl() + "|" + pd.getVersion());
            map.put("status", pd.getStatus());
            map.put("title", extractTitle(pd));
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private String extractTitle(ProtocolDefinition pd) {
        try {
            if (pd.getDefinition() != null) {
                JsonNode node = objectMapper.readTree(pd.getDefinition());
                JsonNode titleNode = node.get("title");
                if (titleNode != null && !titleNode.isNull()) {
                    return titleNode.asText();
                }
            }
        } catch (Exception ignored) {
        }
        return pd.getUrl().substring(pd.getUrl().lastIndexOf('/') + 1);
    }

    @GetMapping("/facilities")
    @Cacheable(value = "lookups", key = "'facilities'")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getFacilities() {
        List<String> facilityIds = complianceEventLogRepository.findDistinctFacilityIds();
        Map<String, String> nameMap = new LinkedHashMap<>();
        for (Object[] row : complianceEventLogRepository.findFacilityNames()) {
            nameMap.put((String) row[0], (String) row[1]);
        }
        List<Map<String, String>> result = facilityIds.stream().map(id -> {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("name", nameMap.getOrDefault(id, id));
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/practitioners")
    @Cacheable(value = "lookups", key = "'practitioners'")
    public ResponseEntity<ApiResponse<List<String>>> getPractitioners() {
        List<String> practitioners = complianceEventLogRepository.findDistinctPractitioners();
        return ResponseEntity.ok(ApiResponse.ok(practitioners));
    }

    @GetMapping("/sources")
    @Cacheable(value = "lookups", key = "'sources'")
    public ResponseEntity<ApiResponse<List<String>>> getSources() {
        List<String> sources = inboundEventRepository.findDistinctSources();
        return ResponseEntity.ok(ApiResponse.ok(sources));
    }

    @GetMapping("/patients")
    @Cacheable(value = "lookups", key = "'patients'")
    public ResponseEntity<ApiResponse<List<String>>> getPatients() {
        List<String> patients = protocolInstanceRepository.findDistinctPatientIds();
        return ResponseEntity.ok(ApiResponse.ok(patients));
    }
}
