package org.openphc.cce.insights.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.DeviationType;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.ComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.FacilitySummaryDto;
import org.openphc.cce.insights.web.dto.PatientComplianceDto;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplianceSummaryService {

    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final DeviationRepository deviationRepository;
    private final EventLogRepository eventLogRepository;

    @Cacheable(value = "analytics", key = "'compliance-all-' + (#facilityId ?: 'all')")
    public ComplianceSummaryDto getAllProtocolsComplianceSummary(String facilityId) {
        List<ProtocolInstance> instances = protocolInstanceRepository.findAll();
        if (facilityId != null && !facilityId.isEmpty()) {
            Set<UUID> facilityInstanceIds = getFacilityInstanceIds(facilityId);
            instances = instances.stream()
                    .filter(pi -> facilityInstanceIds.contains(pi.getId()))
                    .collect(Collectors.toList());
        }
        if (instances.isEmpty()) {
            return ComplianceSummaryDto.builder()
                    .totalEnrollments(0).compliantPatients(0).complianceRate(0.0)
                    .stepMetrics(ComplianceSummaryDto.StepMetrics.builder().build())
                    .deviationCount(0).deviationBreakdown(Map.of())
                    .build();
        }

        long totalSteps = 0, completed = 0, onTime = 0, late = 0, early = 0, overdue = 0, missed = 0, due = 0, pending = 0;
        long totalDeviations = 0, overdueDeviations = 0, missedDeviations = 0, orderViolationDeviations = 0;
        long compliantPatients = 0;

        for (ProtocolInstance pi : instances) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
            totalSteps += steps.size();
            for (StepInstance si : steps) {
                switch (si.getState()) {
                    case COMPLETED -> {
                        completed++;
                        if (si.getCompletionStatus() != null) {
                            switch (si.getCompletionStatus()) {
                                case EARLY -> early++;
                                case ON_TIME -> onTime++;
                                case LATE -> late++;
                            }
                        }
                    }
                    case OVERDUE -> overdue++;
                    case MISSED -> missed++;
                    case SKIPPED -> completed++;
                    case DUE -> due++;
                    case PENDING -> pending++;
                }
            }
            List<Deviation> deviations = deviationRepository.findByProtocolInstanceId(pi.getId());
            totalDeviations += deviations.size();
            if (deviations.isEmpty()) {
                compliantPatients++;
            }
            for (Deviation d : deviations) {
                switch (d.getDeviationType()) {
                    case OVERDUE -> overdueDeviations++;
                    case MISSED -> missedDeviations++;
                    case ORDER_VIOLATION -> orderViolationDeviations++;
                }
            }
        }

        double complianceRate = instances.size() > 0 ? (double) compliantPatients / instances.size() : 0.0;

        return ComplianceSummaryDto.builder()
                .totalEnrollments(instances.size())
                .compliantPatients(compliantPatients)
                .complianceRate(Math.round(complianceRate * 100.0) / 100.0)
                .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                        .totalSteps(totalSteps).completed(completed).onTime(onTime)
                        .late(late).early(early).overdue(overdue).missed(missed).due(due).pending(pending)
                        .build())
                .deviationCount(totalDeviations)
                .deviationBreakdown(Map.of("overdue", overdueDeviations, "missed", missedDeviations, "orderViolation", orderViolationDeviations))
                .build();
    }

    @Cacheable(value = "analytics", key = "'compliance-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all')")
    public ComplianceSummaryDto getProtocolComplianceSummary(UUID protocolDefinitionId, String facilityId) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<ProtocolInstance> instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        if (facilityId != null && !facilityId.isEmpty()) {
            Set<UUID> facilityInstanceIds = getFacilityInstanceIds(facilityId);
            instances = instances.stream()
                    .filter(pi -> facilityInstanceIds.contains(pi.getId()))
                    .collect(Collectors.toList());
        }
        if (instances.isEmpty()) {
            return buildEmptySummary(pd);
        }

        Map<String, Long> statusBreakdown = instances.stream()
                .collect(Collectors.groupingBy(pi -> pi.getStatus().name().toLowerCase(), Collectors.counting()));

        long totalSteps = 0, completed = 0, onTime = 0, late = 0, early = 0, overdue = 0, missed = 0, due = 0, pending = 0;
        long totalDeviations = 0, overdueDeviations = 0, missedDeviations = 0, orderViolationDeviations = 0;
        long compliantPatients = 0;

        for (ProtocolInstance pi : instances) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
            totalSteps += steps.size();
            for (StepInstance si : steps) {
                switch (si.getState()) {
                    case COMPLETED -> {
                        completed++;
                        if (si.getCompletionStatus() != null) {
                            switch (si.getCompletionStatus()) {
                                case EARLY -> early++;
                                case ON_TIME -> onTime++;
                                case LATE -> late++;
                            }
                        }
                    }
                    case OVERDUE -> overdue++;
                    case MISSED -> missed++;
                    case SKIPPED -> completed++;
                    case DUE -> due++;
                    case PENDING -> pending++;
                }
            }
            List<Deviation> deviations = deviationRepository.findByProtocolInstanceId(pi.getId());
            totalDeviations += deviations.size();
            if (deviations.isEmpty()) {
                compliantPatients++;
            }
            for (Deviation d : deviations) {
                switch (d.getDeviationType()) {
                    case OVERDUE -> overdueDeviations++;
                    case MISSED -> missedDeviations++;
                    case ORDER_VIOLATION -> orderViolationDeviations++;
                }
            }
        }

        double complianceRate = instances.size() > 0 ? (double) compliantPatients / instances.size() : 0.0;

        return ComplianceSummaryDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalEnrollments(instances.size())
                .compliantPatients(compliantPatients)
                .statusBreakdown(statusBreakdown)
                .complianceRate(Math.round(complianceRate * 100.0) / 100.0)
                .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                        .totalSteps(totalSteps).completed(completed).onTime(onTime)
                        .late(late).early(early).overdue(overdue).missed(missed).due(due).pending(pending)
                        .build())
                .deviationCount(totalDeviations)
                .deviationBreakdown(Map.of("overdue", overdueDeviations, "missed", missedDeviations, "orderViolation", orderViolationDeviations))
                .build();
    }

    @Cacheable(value = "analytics", key = "'protocol-patients-' + #protocolDefinitionId + '-' + #statusFilter + '-' + #patientIdFilter + '-' + #limit + '-' + #offset")
    public List<PatientComplianceDto> getProtocolPatients(UUID protocolDefinitionId, String statusFilter, String patientIdFilter, int limit, int offset) {
        protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        Pageable pageable = PageRequest.of(offset / Math.max(limit, 1), limit, Sort.by(Sort.Direction.DESC, "enrolledAt"));
        Page<ProtocolInstance> page;
        if (patientIdFilter != null && !patientIdFilter.isEmpty()) {
            page = protocolInstanceRepository.findByProtocolDefinitionIdAndPatientIdContaining(protocolDefinitionId, patientIdFilter, pageable);
        } else if (statusFilter != null && !statusFilter.isEmpty()) {
            // Fetch all sorted, then filter in-memory (status is computed, not a DB column)
            page = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId, pageable);
        } else {
            page = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId, pageable);
        }

        List<PatientComplianceDto> results = new ArrayList<>();
        for (ProtocolInstance pi : page.getContent()) {
            List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
            long completedCount = steps.stream()
                    .filter(s -> s.getState() == StepState.COMPLETED || s.getState() == StepState.SKIPPED)
                    .count();
            double rate = steps.isEmpty() ? 0.0 : (double) completedCount / steps.size();
            String category = computeCategory(steps);

            if (statusFilter != null && !statusFilter.equalsIgnoreCase(category)) {
                continue;
            }

            long activeDevs = deviationRepository.findByProtocolInstanceId(pi.getId()).size();

            results.add(PatientComplianceDto.builder()
                    .patientId(pi.getPatientId())
                    .protocolInstanceId(pi.getId().toString())
                    .protocolCanonical(pi.getProtocolCanonical())
                    .enrolledAt(pi.getEnrolledAt())
                    .status(pi.getStatus().name().toLowerCase())
                    .complianceRate(Math.round(rate * 100.0) / 100.0)
                    .complianceCategory(category)
                    .stepsCompleted(completedCount)
                    .totalSteps(steps.size())
                    .activeDeviations(activeDevs)
                    .build());
        }
        return results;
    }

    @Cacheable(value = "analytics", key = "'facility-' + #facilityId")
    public FacilitySummaryDto getFacilityComplianceSummary(String facilityId) {
        // Get protocol instances belonging to this facility via event_log
        List<Object[]> rows = eventLogRepository.findPatientsByFacility(facilityId);
        Set<UUID> facilityInstanceIds = new HashSet<>();
        Set<String> patients = new LinkedHashSet<>();
        for (Object[] row : rows) {
            patients.add((String) row[1]);
            facilityInstanceIds.add((UUID) row[2]);
        }

        List<ProtocolInstance> facilityInstances = facilityInstanceIds.isEmpty()
                ? Collections.emptyList()
                : protocolInstanceRepository.findAll().stream()
                        .filter(pi -> facilityInstanceIds.contains(pi.getId()))
                        .collect(Collectors.toList());

        Map<UUID, List<ProtocolInstance>> byProtocol = facilityInstances.stream()
                .collect(Collectors.groupingBy(ProtocolInstance::getProtocolDefinitionId));

        List<FacilitySummaryDto.ProtocolBreakdown> breakdowns = new ArrayList<>();
        long totalCompleted = 0, totalSteps = 0;

        for (Map.Entry<UUID, List<ProtocolInstance>> entry : byProtocol.entrySet()) {
            ProtocolDefinition pd = protocolDefinitionRepository.findById(entry.getKey()).orElse(null);
            if (pd == null) continue;

            List<ProtocolInstance> pInstances = entry.getValue();
            long pCompleted = 0, pTotal = 0, activeDevs = 0;
            for (ProtocolInstance pi : pInstances) {
                List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceId(pi.getId());
                pTotal += steps.size();
                pCompleted += steps.stream().filter(s -> s.getCompletedAt() != null).count();
                activeDevs += deviationRepository.findByProtocolInstanceId(pi.getId()).size();
            }
            totalCompleted += pCompleted;
            totalSteps += pTotal;

            double pRate = pTotal > 0 ? Math.round((double) pCompleted / pTotal * 100.0) / 100.0 : 0;
            breakdowns.add(FacilitySummaryDto.ProtocolBreakdown.builder()
                    .protocolDefinitionId(entry.getKey().toString())
                    .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                    .enrollments(pInstances.size())
                    .complianceRate(pRate)
                    .activeDeviations(activeDevs)
                    .build());
        }

        double overallRate = totalSteps > 0 ? Math.round((double) totalCompleted / totalSteps * 100.0) / 100.0 : 0;

        return FacilitySummaryDto.builder()
                .facilityId(facilityId)
                .totalPatients(patients.size())
                .totalEnrollments(facilityInstances.size())
                .overallComplianceRate(overallRate)
                .protocolBreakdown(breakdowns)
                .build();
    }

    private String computeCategory(List<StepInstance> steps) {
        boolean hasMissed = steps.stream().anyMatch(s -> s.getState() == StepState.MISSED);
        if (hasMissed) return "non_compliant";
        boolean hasOverdue = steps.stream().anyMatch(s -> s.getState() == StepState.OVERDUE);
        if (hasOverdue) return "at_risk";
        return "on_track";
    }

    private ComplianceSummaryDto buildEmptySummary(ProtocolDefinition pd) {
        return ComplianceSummaryDto.builder()
                .protocolDefinitionId(pd.getId())
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalEnrollments(0)
                .statusBreakdown(Map.of())
                .complianceRate(0.0)
                .stepMetrics(ComplianceSummaryDto.StepMetrics.builder().build())
                .deviationCount(0)
                .deviationBreakdown(Map.of("overdue", 0L, "missed", 0L, "orderViolation", 0L))
                .build();
    }

    private Set<UUID> getFacilityInstanceIds(String facilityId) {
        List<Object[]> rows = eventLogRepository.findPatientsByFacility(facilityId);
        Set<UUID> ids = new HashSet<>();
        for (Object[] row : rows) {
            ids.add((UUID) row[2]);
        }
        return ids;
    }
}
