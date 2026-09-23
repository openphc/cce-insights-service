package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.web.dto.PractitionerRankingDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PractitionerRankingService {

    private final ComplianceEventLogRepository complianceEventLogRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final DeviationRepository deviationRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "analytics", key = "'practitioner-rankings-' + #sortBy + '-' + #order + '-' + #limit + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all') + '-' + (#facilityId ?: 'all') + '-' + (#protocolDefinitionId ?: 'all')")
    public List<PractitionerRankingDto> getRankings(String sortBy, String order, int limit,
                                                     OffsetDateTime startDate, OffsetDateTime endDate,
                                                     String facilityId, UUID protocolDefinitionId) {

        // When scoped to a protocol, only patients enrolled in it count towards a
        // practitioner's totals - candidate events are filtered against this set below.
        Set<String> protocolPatientIds = null;
        if (protocolDefinitionId != null) {
            protocolPatientIds = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId)
                    .stream().map(ProtocolInstance::getPatientId).collect(Collectors.toSet());
        }

        // Build facility name lookup
        Map<String, String> facilityNameMap = new LinkedHashMap<>();
        for (Object[] row : complianceEventLogRepository.findFacilityNames()) {
            facilityNameMap.put((String) row[0], (String) row[1]);
        }

        // Practitioner summary derived by parsing each candidate event's resource body directly -
        // inbound_event_logs.practitioner_ref/practitioner_display are ClickHouse MATERIALIZED
        // columns that look for a "practitionerRef"/"practitionerDisplay" field inside the
        // CloudEvent body, which no real FHIR resource has and no adaptor has ever populated, so
        // they're always empty. See PractitionerExtractor for the actual per-resource-type parsing.
        Map<String, String> displayMap = new LinkedHashMap<>();
        Map<String, String> facilityMap = new LinkedHashMap<>();
        Map<String, Long> eventCountMap = new LinkedHashMap<>();
        Map<String, Set<String>> patientsByRef = new LinkedHashMap<>();

        List<Object[]> candidateEvents = complianceEventLogRepository
                .findPractitionerCandidateEvents(startDate, endDate, facilityId);
        for (Object[] row : candidateEvents) {
            String subject = (String) row[0];
            String rowFacilityId = (String) row[1];
            String dataJson = (String) row[2];
            if (dataJson == null || dataJson.isEmpty()) continue;
            if (protocolPatientIds != null && !protocolPatientIds.contains(subject)) continue;

            PractitionerExtractor.PractitionerRef found;
            try {
                JsonNode root = objectMapper.readTree(dataJson);
                found = PractitionerExtractor.extract(root);
            } catch (Exception e) {
                log.debug("Failed to parse resource body for practitioner extraction: {}", e.getMessage());
                continue;
            }
            if (found == null) continue;

            String ref = found.reference();
            if (found.display() != null) {
                displayMap.put(ref, found.display());
            } else {
                displayMap.putIfAbsent(ref, null);
            }
            facilityMap.putIfAbsent(ref, rowFacilityId);
            eventCountMap.merge(ref, 1L, Long::sum);
            patientsByRef.computeIfAbsent(ref, k -> new LinkedHashSet<>()).add(subject);
        }

        Map<String, Long> patientCountMap = new LinkedHashMap<>();
        Set<String> allPatientIds = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> e : patientsByRef.entrySet()) {
            patientCountMap.put(e.getKey(), (long) e.getValue().size());
            allPatientIds.addAll(e.getValue());
        }

        // Step compliance per patient, then rolled up to every practitioner that patient maps to
        // (a patient can see more than one practitioner across different events).
        Map<String, long[]> complianceByPatient = new HashMap<>(); // patientId -> [total, completed]
        for (Object[] row : stepInstanceRepository.findStepComplianceByPatientIds(new ArrayList<>(allPatientIds))) {
            complianceByPatient.put((String) row[0],
                    new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }

        Map<String, Long> totalStepsMap = new LinkedHashMap<>();
        Map<String, Long> completedStepsMap = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : patientsByRef.entrySet()) {
            long total = 0, completed = 0;
            for (String patientId : e.getValue()) {
                long[] c = complianceByPatient.get(patientId);
                if (c != null) {
                    total += c[0];
                    completed += c[1];
                }
            }
            totalStepsMap.put(e.getKey(), total);
            completedStepsMap.put(e.getKey(), completed);
        }

        // Deviations by practitioner (via matched event)
        Map<String, Long> deviationCountMap = new LinkedHashMap<>();
        // Reuse existing deviation data joined through the step → event → practitioner path
        // For now, set to 0 — can be enhanced with a dedicated query if needed

        // Build DTOs
        List<PractitionerRankingDto> rankings = displayMap.keySet().stream().map(ref -> {
            long totalSteps = totalStepsMap.getOrDefault(ref, 0L);
            long completedSteps = completedStepsMap.getOrDefault(ref, 0L);
            double complianceRate = totalSteps > 0
                    ? Math.round((double) completedSteps / totalSteps * 1000.0) / 10.0
                    : 0.0;

            return PractitionerRankingDto.builder()
                    .practitionerRef(ref)
                    .practitionerName(displayMap.get(ref))
                    .facilityId(facilityMap.get(ref))
                    .facilityName(facilityNameMap.getOrDefault(facilityMap.get(ref), facilityMap.get(ref)))
                    .totalEvents(eventCountMap.getOrDefault(ref, 0L))
                    .totalPatients(patientCountMap.getOrDefault(ref, 0L))
                    .totalSteps(totalSteps)
                    .completedSteps(completedSteps)
                    .complianceRate(complianceRate)
                    .activeDeviations(deviationCountMap.getOrDefault(ref, 0L))
                    .build();
        }).collect(Collectors.toList());

        // Sort
        Comparator<PractitionerRankingDto> comparator = switch (sortBy != null ? sortBy : "complianceRate") {
            case "complianceRate" -> Comparator.comparingDouble(PractitionerRankingDto::getComplianceRate);
            case "totalPatients" -> Comparator.comparingLong(PractitionerRankingDto::getTotalPatients);
            case "totalEvents" -> Comparator.comparingLong(PractitionerRankingDto::getTotalEvents);
            default -> Comparator.comparingDouble(PractitionerRankingDto::getComplianceRate);
        };

        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }

        rankings.sort(comparator);

        // Assign rank and limit
        List<PractitionerRankingDto> ranked = new ArrayList<>();
        int rank = 1;
        for (PractitionerRankingDto dto : rankings) {
            if (rank > limit) break;
            ranked.add(PractitionerRankingDto.builder()
                    .rank(rank++)
                    .practitionerRef(dto.getPractitionerRef())
                    .practitionerName(dto.getPractitionerName())
                    .facilityId(dto.getFacilityId())
                    .facilityName(dto.getFacilityName())
                    .totalPatients(dto.getTotalPatients())
                    .complianceRate(dto.getComplianceRate())
                    .totalSteps(dto.getTotalSteps())
                    .completedSteps(dto.getCompletedSteps())
                    .activeDeviations(dto.getActiveDeviations())
                    .totalEvents(dto.getTotalEvents())
                    .build());
        }
        return ranked;
    }

    /**
     * Distinct practitioner references seen across all time, for lookup dropdowns.
     * Same extraction path as {@link #getRankings} - see its comment on why the
     * inbound_event_logs.practitioner_ref column can't be used directly.
     */
    @Cacheable(value = "lookups", key = "'practitioners'")
    public List<String> getDistinctPractitionerRefs() {
        Set<String> refs = new TreeSet<>();
        for (Object[] row : complianceEventLogRepository.findPractitionerCandidateEvents(null, null, null)) {
            String dataJson = (String) row[2];
            if (dataJson == null || dataJson.isEmpty()) continue;
            try {
                JsonNode root = objectMapper.readTree(dataJson);
                PractitionerExtractor.PractitionerRef found = PractitionerExtractor.extract(root);
                if (found != null) refs.add(found.reference());
            } catch (Exception e) {
                log.debug("Failed to parse resource body for practitioner extraction: {}", e.getMessage());
            }
        }
        return new ArrayList<>(refs);
    }
}
