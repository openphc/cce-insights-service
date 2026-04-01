package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.EventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityRankingService {

    private final EventLogRepository eventLogRepository;
    private final DeviationRepository deviationRepository;

    @Cacheable(value = "analytics", key = "'rankings-' + #sortBy + '-' + #limit")
    public List<FacilityRankingDto> getRankings(OffsetDateTime startDate, OffsetDateTime endDate,
                                                 String sortBy, int limit) {
        List<Object[]> facilityEvents = eventLogRepository.findFacilityEventCounts(null);

        Map<String, Long> eventCountMap = new LinkedHashMap<>();
        Map<String, Long> activePatientMap = new LinkedHashMap<>();

        for (Object[] row : facilityEvents) {
            String facilityId = (String) row[0];
            eventCountMap.put(facilityId, ((Number) row[1]).longValue());
        }

        List<Object[]> activePatients = eventLogRepository.findActivePatientsByFacility(null);
        for (Object[] row : activePatients) {
            activePatientMap.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<Object[]> deviationRows = deviationRepository.countDeviationsByFacility();
        Map<String, Long> deviationCountMap = new LinkedHashMap<>();
        for (Object[] row : deviationRows) {
            deviationCountMap.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<FacilityRankingDto> rankings = eventCountMap.keySet().stream().map(facilityId -> {
            long events = eventCountMap.getOrDefault(facilityId, 0L);
            long patients = activePatientMap.getOrDefault(facilityId, 0L);
            long deviations = deviationCountMap.getOrDefault(facilityId, 0L);
            double complianceRate = events > 0 && deviations > 0
                    ? Math.round((1.0 - (double) deviations / events) * 1000.0) / 10.0
                    : 100.0;

            return FacilityRankingDto.builder()
                    .facilityId(facilityId)
                    .totalEvents(events)
                    .totalEnrollments(patients)
                    .activeDeviations(deviations)
                    .complianceRate(complianceRate)
                    .build();
        }).collect(Collectors.toList());

        Comparator<FacilityRankingDto> comparator = switch (sortBy != null ? sortBy : "events") {
            case "compliance" -> Comparator.comparingDouble(FacilityRankingDto::getComplianceRate).reversed();
            case "deviations" -> Comparator.comparingLong(FacilityRankingDto::getActiveDeviations).reversed();
            case "patients" -> Comparator.comparingLong(FacilityRankingDto::getTotalEnrollments).reversed();
            default -> Comparator.comparingLong(FacilityRankingDto::getTotalEvents).reversed();
        };

        rankings.sort(comparator);

        int rank = 1;
        List<FacilityRankingDto> ranked = new ArrayList<>();
        for (FacilityRankingDto dto : rankings) {
            if (rank > limit) break;
            ranked.add(FacilityRankingDto.builder()
                    .rank(rank++)
                    .facilityId(dto.getFacilityId())
                    .totalEvents(dto.getTotalEvents())
                    .totalEnrollments(dto.getTotalEnrollments())
                    .activeDeviations(dto.getActiveDeviations())
                    .complianceRate(dto.getComplianceRate())
                    .build());
        }
        return ranked;
    }
}
