package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacilityRankingService {

    private final ComplianceEventLogRepository complianceEventLogRepository;
    private final DeviationRepository deviationRepository;
    private final StepInstanceRepository stepInstanceRepository;

    @Cacheable(value = "analytics", key = "'rankings-' + #sortBy + '-' + #order + '-' + #limit + '-' + #startDate + '-' + #endDate")
    public List<FacilityRankingDto> getRankings(OffsetDateTime startDate, OffsetDateTime endDate,
                                                 String sortBy, String order, int limit) {
        List<Object[]> facilityEvents = complianceEventLogRepository.findFacilityEventCounts(null);

        // Build facility name lookup
        Map<String, String> facilityNameMap = new LinkedHashMap<>();
        for (Object[] row : complianceEventLogRepository.findFacilityNames()) {
            facilityNameMap.put((String) row[0], (String) row[1]);
        }

        Map<String, Long> eventCountMap = new LinkedHashMap<>();
        Map<String, Long> activePatientMap = new LinkedHashMap<>();

        for (Object[] row : facilityEvents) {
            String facilityId = (String) row[0];
            eventCountMap.put(facilityId, ((Number) row[2]).longValue());
        }

        List<Object[]> activePatients = complianceEventLogRepository.findActivePatientsByFacility(null);
        for (Object[] row : activePatients) {
            activePatientMap.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<Object[]> deviationRows = deviationRepository.countDeviationsByFacility();
        Map<String, Long> deviationCountMap = new LinkedHashMap<>();
        for (Object[] row : deviationRows) {
            deviationCountMap.put((String) row[0], ((Number) row[1]).longValue());
        }

        // Step-based compliance: completed+skipped / total steps per facility
        List<Object[]> stepComplianceRows = stepInstanceRepository.findStepComplianceByFacility();
        Map<String, Long> totalStepsMap = new LinkedHashMap<>();
        Map<String, Long> completedStepsMap = new LinkedHashMap<>();
        for (Object[] row : stepComplianceRows) {
            String facilityId = (String) row[0];
            totalStepsMap.put(facilityId, ((Number) row[1]).longValue());
            completedStepsMap.put(facilityId, ((Number) row[2]).longValue());
        }

        // Referral event counts per facility (outbound = initiated, inbound = closure)
        List<Object[]> referralRows = stepInstanceRepository.findReferralEventCountsByFacility();
        Map<String, Long> outboundEventsMap = new LinkedHashMap<>();
        Map<String, Long> inboundEventsMap = new LinkedHashMap<>();
        for (Object[] row : referralRows) {
            String facilityId = (String) row[0];
            outboundEventsMap.put(facilityId, ((Number) row[1]).longValue());
            inboundEventsMap.put(facilityId, ((Number) row[2]).longValue());
        }

        // Collect all known facility IDs from all sources
        Set<String> allFacilities = new LinkedHashSet<>();
        allFacilities.addAll(eventCountMap.keySet());
        allFacilities.addAll(totalStepsMap.keySet());

        List<FacilityRankingDto> rankings = allFacilities.stream().map(facilityId -> {
            long events = eventCountMap.getOrDefault(facilityId, 0L);
            long patients = activePatientMap.getOrDefault(facilityId, 0L);
            long deviations = deviationCountMap.getOrDefault(facilityId, 0L);
            long totalSteps = totalStepsMap.getOrDefault(facilityId, 0L);
            long completedSteps = completedStepsMap.getOrDefault(facilityId, 0L);
            long outbound = outboundEventsMap.getOrDefault(facilityId, 0L);
            long inbound = inboundEventsMap.getOrDefault(facilityId, 0L);
            double complianceRate = totalSteps > 0
                    ? Math.round((double) completedSteps / totalSteps * 1000.0) / 10.0
                    : 100.0;

            return FacilityRankingDto.builder()
                    .facilityId(facilityId)
                    .facilityName(facilityNameMap.getOrDefault(facilityId, facilityId))
                    .totalEvents(events)
                    .outboundEvents(outbound)
                    .inboundEvents(inbound)
                    .totalEnrollments(patients)
                    .activeDeviations(deviations)
                    .complianceRate(complianceRate)
                    .build();
        }).collect(Collectors.toList());

        Comparator<FacilityRankingDto> comparator = switch (sortBy != null ? sortBy : "complianceRate") {
            case "complianceRate" -> Comparator.comparingDouble(FacilityRankingDto::getComplianceRate);
            case "deviationCount" -> Comparator.comparingLong(FacilityRankingDto::getActiveDeviations);
            case "eventVolume", "totalEvents" -> Comparator.comparingLong(FacilityRankingDto::getTotalEvents);
            default -> Comparator.comparingDouble(FacilityRankingDto::getComplianceRate);
        };

        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }

        rankings.sort(comparator);

        int rank = 1;
        List<FacilityRankingDto> ranked = new ArrayList<>();
        for (FacilityRankingDto dto : rankings) {
            if (rank > limit) break;
            ranked.add(FacilityRankingDto.builder()
                    .rank(rank++)
                    .facilityId(dto.getFacilityId())
                    .facilityName(dto.getFacilityName())
                    .totalEvents(dto.getTotalEvents())
                    .outboundEvents(dto.getOutboundEvents())
                    .inboundEvents(dto.getInboundEvents())
                    .totalEnrollments(dto.getTotalEnrollments())
                    .activeDeviations(dto.getActiveDeviations())
                    .complianceRate(dto.getComplianceRate())
                    .build());
        }
        return ranked;
    }
}
