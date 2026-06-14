package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.AtRiskHotspotDto;
import org.openphc.cce.insights.web.dto.RepeatDeviationPatientDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientRiskService {

    private final DeviationRepository deviationRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final ComplianceEventLogRepository complianceEventLogRepository;

    @Cacheable(value = "analytics", key = "'risk-hotspots'")
    public List<AtRiskHotspotDto> getAtRiskHotspots(OffsetDateTime startDate, OffsetDateTime endDate) {
        // Build facility -> set of patient IDs mapping
        List<Object[]> facilityPatientRows = complianceEventLogRepository.findFacilityPatientMapping();
        Map<String, Set<String>> facilityPatients = new LinkedHashMap<>();
        for (Object[] row : facilityPatientRows) {
            String facilityId = (String) row[0];
            String patientId = (String) row[1];
            facilityPatients.computeIfAbsent(facilityId, k -> new LinkedHashSet<>()).add(patientId);
        }

        // Build facility name lookup
        Map<String, String> facilityNameMap = new LinkedHashMap<>();
        for (Object[] row : complianceEventLogRepository.findFacilityNames()) {
            facilityNameMap.put((String) row[0], (String) row[1]);
        }

        // Build patient -> steps mapping (global, loaded once)
        List<ProtocolInstance> allInstances = protocolInstanceRepository.findAll();
        Map<String, List<StepInstance>> patientSteps = new HashMap<>();
        for (ProtocolInstance pi : allInstances) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
            patientSteps.computeIfAbsent(pi.getPatientId(), k -> new ArrayList<>()).addAll(steps);
        }

        // For each facility, categorize only its own patients
        return facilityPatients.entrySet().stream().map(entry -> {
            String facilityId = entry.getKey();
            Set<String> patients = entry.getValue();
            long onTrack = 0, atRisk = 0, nonCompliant = 0;

            for (String patientId : patients) {
                List<StepInstance> steps = patientSteps.getOrDefault(patientId, Collections.emptyList());
                boolean hasMissed = steps.stream().anyMatch(s -> s.getState() == StepState.MISSED);
                boolean hasOverdue = steps.stream().anyMatch(s -> s.getState() == StepState.OVERDUE);
                if (hasMissed) nonCompliant++;
                else if (hasOverdue) atRisk++;
                else onTrack++;
            }

            long totalPatients = onTrack + atRisk + nonCompliant;
            return AtRiskHotspotDto.builder()
                    .facilityId(facilityId)
                    .facilityName(facilityNameMap.getOrDefault(facilityId, facilityId))
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

    @Cacheable(value = "analytics", key = "'repeat-deviations-' + #minDeviations")
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
                .orderViolationCount(((Number) row[4]).longValue())
                .affectedProtocols(((Number) row[5]).longValue())
                .affectedSteps(((Number) row[6]).longValue())
                .build()
        ).collect(Collectors.toList());
    }
}
