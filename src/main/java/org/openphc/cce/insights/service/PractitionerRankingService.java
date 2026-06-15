package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.PractitionerRankingDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PractitionerRankingService {

    private final ComplianceEventLogRepository complianceEventLogRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final DeviationRepository deviationRepository;

    @Cacheable(value = "analytics", key = "'practitioner-rankings-' + #sortBy + '-' + #order + '-' + #limit + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all') + '-' + (#facilityId ?: 'all')")
    public List<PractitionerRankingDto> getRankings(String sortBy, String order, int limit,
                                                     OffsetDateTime startDate, OffsetDateTime endDate,
                                                     String facilityId) {

        // Practitioner summary: ref, display, facilityId, totalEvents, totalPatients
        List<Object[]> summaryRows = complianceEventLogRepository.findPractitionerSummaryFiltered(startDate, endDate, facilityId);

        // Build facility name lookup
        Map<String, String> facilityNameMap = new LinkedHashMap<>();
        for (Object[] row : complianceEventLogRepository.findFacilityNames()) {
            facilityNameMap.put((String) row[0], (String) row[1]);
        }

        // Aggregate by practitioner_ref (may appear in multiple facilities)
        Map<String, String> displayMap = new LinkedHashMap<>();
        Map<String, String> facilityMap = new LinkedHashMap<>();
        Map<String, Long> eventCountMap = new LinkedHashMap<>();
        Map<String, Long> patientCountMap = new LinkedHashMap<>();

        for (Object[] row : summaryRows) {
            String ref = (String) row[0];
            String display = (String) row[1];
            String rowFacilityId = (String) row[2];
            long events = ((Number) row[3]).longValue();
            long patients = ((Number) row[4]).longValue();

            displayMap.putIfAbsent(ref, display);
            facilityMap.putIfAbsent(ref, rowFacilityId);
            eventCountMap.merge(ref, events, Long::sum);
            patientCountMap.merge(ref, patients, Long::sum);
        }

        // Step compliance by practitioner
        List<Object[]> complianceRows = stepInstanceRepository.findStepComplianceByPractitionerFiltered(startDate, endDate, facilityId);
        Map<String, Long> totalStepsMap = new LinkedHashMap<>();
        Map<String, Long> completedStepsMap = new LinkedHashMap<>();
        for (Object[] row : complianceRows) {
            String ref = (String) row[0];
            totalStepsMap.put(ref, ((Number) row[1]).longValue());
            completedStepsMap.put(ref, ((Number) row[2]).longValue());
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
}
