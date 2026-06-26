package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
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
    private final DailyKpiRepository dailyKpiRepository;

    @Cacheable(value = "analytics",
            key = "'risk-hotspots-' + (#protocolDefinitionId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public List<AtRiskHotspotDto> getAtRiskHotspots(UUID protocolDefinitionId,
                                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        // Build facility -> set of patient IDs mapping
        List<Object[]> facilityPatientRows = complianceEventLogRepository.findFacilityPatientMapping();
        Map<String, Set<String>> facilityPatients = new LinkedHashMap<>();
        for (Object[] row : facilityPatientRows) {
            String facilityId = (String) row[0];
            String patientId = (String) row[1];
            facilityPatients.computeIfAbsent(facilityId, k -> new LinkedHashSet<>()).add(patientId);
        }

        // Build facility name lookup from canonical facility table
        Map<String, String> facilityNameMap = new LinkedHashMap<>();
        for (Object[] row : dailyKpiRepository.getFacilityReference()) {
            facilityNameMap.put((String) row[0], (String) row[1]);
        }

        // Narrow protocol_instances to the selected protocol (when provided) and to
        // enrollments in the selected date range (when provided). Steps inherit the scope.
        List<ProtocolInstance> allInstances;
        boolean hasRange = startDate != null || endDate != null;
        if (protocolDefinitionId != null && hasRange) {
            allInstances = protocolInstanceRepository.findByProtocolDefinitionIdAndEnrolledBetween(
                    protocolDefinitionId, startDate, endDate);
        } else if (protocolDefinitionId != null) {
            allInstances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        } else if (hasRange) {
            allInstances = protocolInstanceRepository.findEnrolledBetween(startDate, endDate);
        } else {
            allInstances = protocolInstanceRepository.findAll();
        }
        Map<UUID, String> instanceToPatient = allInstances.stream()
                .filter(pi -> pi.getPatientId() != null)
                .collect(Collectors.toMap(ProtocolInstance::getId, ProtocolInstance::getPatientId, (a, b) -> a));
        Map<String, List<StepInstance>> patientSteps = new HashMap<>();
        for (StepInstance si : stepInstanceRepository.findByProtocolInstanceIdIn(
                new ArrayList<>(instanceToPatient.keySet()))) {
            String patientId = instanceToPatient.get(si.getProtocolInstanceId());
            if (patientId != null) {
                patientSteps.computeIfAbsent(patientId, k -> new ArrayList<>()).add(si);
            }
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

    @Cacheable(value = "analytics",
            key = "'repeat-deviations-' + #minDeviations + '-' + (#facilityId ?: 'all') + '-' + (#protocolDefinitionId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public List<RepeatDeviationPatientDto> getRepeatDeviationPatients(int minDeviations,
                                                                       String facilityId,
                                                                       UUID protocolDefinitionId,
                                                                       OffsetDateTime startDate,
                                                                       OffsetDateTime endDate) {
        List<Object[]> rows = deviationRepository.findRepeatDeviationPatients(
                minDeviations, facilityId, startDate, endDate);

        // Narrow the result to the selected protocol when set. The SQL aggregates by
        // patient across all protocols, so we filter the affected-protocols dimension
        // by restricting rows to patients enrolled in the requested protocol.
        if (protocolDefinitionId != null) {
            Set<String> patientsInProtocol = new HashSet<>();
            for (ProtocolInstance pi : protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId)) {
                if (pi.getPatientId() != null) patientsInProtocol.add(pi.getPatientId());
            }
            rows = rows.stream()
                    .filter(r -> patientsInProtocol.contains((String) r[0]))
                    .collect(Collectors.toList());
        }

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
