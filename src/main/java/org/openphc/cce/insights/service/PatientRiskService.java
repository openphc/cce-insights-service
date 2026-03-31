package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.EventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.AtRiskHotspotDto;
import org.openphc.cce.insights.web.dto.RepeatDeviationPatientDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientRiskService {

    private final DeviationRepository deviationRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final EventLogRepository eventLogRepository;

    public List<AtRiskHotspotDto> getAtRiskHotspots(OffsetDateTime startDate, OffsetDateTime endDate) {
        List<Object[]> facilityPatients = eventLogRepository.findActivePatientsByFacility(null);

        List<ProtocolInstance> allInstances = protocolInstanceRepository.findAll();
        Map<String, List<StepInstance>> patientSteps = new HashMap<>();
        for (ProtocolInstance pi : allInstances) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
            patientSteps.computeIfAbsent(pi.getPatientId(), k -> new ArrayList<>()).addAll(steps);
        }

        List<Object[]> facilityEventCounts = eventLogRepository.findFacilityEventCounts(null);
        Map<String, Long> facilityTotals = new LinkedHashMap<>();
        for (Object[] row : facilityEventCounts) {
            facilityTotals.put((String) row[0], ((Number) row[1]).longValue());
        }

        return facilityTotals.keySet().stream().map(facilityId -> {
            long total = facilityTotals.getOrDefault(facilityId, 0L);
            long onTrack = 0, atRisk = 0, nonCompliant = 0;

            for (Map.Entry<String, List<StepInstance>> entry : patientSteps.entrySet()) {
                List<StepInstance> steps = entry.getValue();
                boolean hasMissed = steps.stream().anyMatch(s -> s.getState() == StepState.MISSED);
                boolean hasOverdue = steps.stream().anyMatch(s -> s.getState() == StepState.OVERDUE);
                if (hasMissed) nonCompliant++;
                else if (hasOverdue) atRisk++;
                else onTrack++;
            }

            long totalPatients = onTrack + atRisk + nonCompliant;
            return AtRiskHotspotDto.builder()
                    .facilityId(facilityId)
                    .totalPatients(totalPatients)
                    .onTrack(AtRiskHotspotDto.CategoryCount.builder()
                            .count(onTrack)
                            .percentage(totalPatients > 0 ? Math.round((double) onTrack / totalPatients * 1000.0) / 10.0 : 0)
                            .build())
                    .atRisk(AtRiskHotspotDto.CategoryCount.builder()
                            .count(atRisk)
                            .percentage(totalPatients > 0 ? Math.round((double) atRisk / totalPatients * 1000.0) / 10.0 : 0)
                            .build())
                    .nonCompliant(AtRiskHotspotDto.CategoryCount.builder()
                            .count(nonCompliant)
                            .percentage(totalPatients > 0 ? Math.round((double) nonCompliant / totalPatients * 1000.0) / 10.0 : 0)
                            .build())
                    .build();
        }).collect(Collectors.toList());
    }

    public List<RepeatDeviationPatientDto> getRepeatDeviationPatients(int minDeviations,
                                                                       OffsetDateTime startDate,
                                                                       OffsetDateTime endDate) {
        List<Object[]> rows = deviationRepository.findRepeatDeviationPatients(
                minDeviations, null, startDate, endDate);

        return rows.stream().map(row -> RepeatDeviationPatientDto.builder()
                .patientId((String) row[0])
                .totalDeviations(((Number) row[1]).longValue())
                .overdueCount(((Number) row[2]).longValue())
                .missedCount(((Number) row[3]).longValue())
                .affectedProtocols(((Number) row[4]).longValue())
                .affectedSteps(((Number) row[5]).longValue())
                .build()
        ).collect(Collectors.toList());
    }
}
