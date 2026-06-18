package org.openphc.cce.insights.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.ComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.FacilitySummaryDto;
import org.openphc.cce.insights.web.dto.PatientComplianceDto;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplianceSummaryService {

    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final DeviationRepository deviationRepository;
    private final ComplianceEventLogRepository complianceEventLogRepository;

    @Cacheable(value = "analytics", key = "'compliance-all-' + (#facilityId ?: 'all')")
    public ComplianceSummaryDto getAllProtocolsComplianceSummary(String facilityId) {
        boolean hasFacility = facilityId != null && !facilityId.isEmpty();
        Object[] sm = hasFacility
                ? stepInstanceRepository.aggregateStepMetricsByFacility(facilityId)
                : stepInstanceRepository.aggregateStepMetricsAll();
        Object[] dm = hasFacility
                ? deviationRepository.aggregateDeviationMetricsByFacility(facilityId)
                : deviationRepository.aggregateDeviationMetricsAll();

        long totalEnrollments = toLong(sm[9]);
        if (totalEnrollments == 0) {
            return ComplianceSummaryDto.builder()
                    .totalEnrollments(0).compliantPatients(0).complianceRate(0.0)
                    .stepMetrics(ComplianceSummaryDto.StepMetrics.builder().build())
                    .deviationCount(0).deviationBreakdown(Map.of())
                    .build();
        }

        long compliantPatients = toLong(dm[0]);
        double complianceRate  = (double) compliantPatients / totalEnrollments;

        return ComplianceSummaryDto.builder()
                .totalEnrollments(totalEnrollments)
                .compliantPatients(compliantPatients)
                .complianceRate(Math.round(complianceRate * 100.0) / 100.0)
                .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                        .totalSteps(toLong(sm[8])).completed(toLong(sm[0]))
                        .onTime(toLong(sm[6])).late(toLong(sm[7])).early(toLong(sm[5]))
                        .overdue(toLong(sm[1])).missed(toLong(sm[2])).due(toLong(sm[3])).pending(toLong(sm[4]))
                        .build())
                .deviationCount(toLong(dm[1]))
                .deviationBreakdown(Map.of(
                        "overdue",        toLong(dm[2]),
                        "missed",         toLong(dm[3]),
                        "orderViolation", toLong(dm[4])))
                .build();
    }

    @Cacheable(value = "analytics", key = "'compliance-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all')")
    public ComplianceSummaryDto getProtocolComplianceSummary(UUID protocolDefinitionId, String facilityId) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        // Always 2 aggregate queries — no per-instance loop regardless of facility filter
        Object[] sm = hasFacility
                ? stepInstanceRepository.aggregateStepMetricsByProtocolAndFacility(protocolDefinitionId, facilityId)
                : stepInstanceRepository.aggregateStepMetrics(protocolDefinitionId);
        Object[] dm = hasFacility
                ? deviationRepository.aggregateDeviationMetricsByProtocolAndFacility(protocolDefinitionId, facilityId)
                : deviationRepository.aggregateDeviationMetrics(protocolDefinitionId);

        long totalEnrollments = toLong(sm[9]);
        if (totalEnrollments == 0) {
            return buildEmptySummary(pd);
        }

        // Load instances only for statusBreakdown (group by ACTIVE/COMPLETED/etc)
        List<ProtocolInstance> instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        if (hasFacility) {
            Set<UUID> facilityInstanceIds = getFacilityInstanceIds(facilityId);
            instances = instances.stream()
                    .filter(pi -> facilityInstanceIds.contains(pi.getId()))
                    .collect(Collectors.toList());
        }
        Map<String, Long> statusBreakdown = instances.stream()
                .collect(Collectors.groupingBy(pi -> pi.getStatus().name().toLowerCase(), Collectors.counting()));

        long compliantPatients = toLong(dm[0]);
        double complianceRate  = (double) compliantPatients / totalEnrollments;

        return ComplianceSummaryDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalEnrollments(totalEnrollments)
                .compliantPatients(compliantPatients)
                .statusBreakdown(statusBreakdown)
                .complianceRate(Math.round(complianceRate * 100.0) / 100.0)
                .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                        .totalSteps(toLong(sm[8])).completed(toLong(sm[0]))
                        .onTime(toLong(sm[6])).late(toLong(sm[7])).early(toLong(sm[5]))
                        .overdue(toLong(sm[1])).missed(toLong(sm[2])).due(toLong(sm[3])).pending(toLong(sm[4]))
                        .build())
                .deviationCount(toLong(dm[1]))
                .deviationBreakdown(Map.of(
                        "overdue",        toLong(dm[2]),
                        "missed",         toLong(dm[3]),
                        "orderViolation", toLong(dm[4])))
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

        // Batch-load steps and deviation counts for the whole page in 2 queries
        List<UUID> pageIds = page.getContent().stream().map(ProtocolInstance::getId).collect(Collectors.toList());
        Map<UUID, List<StepInstance>> stepsByInstance = stepInstanceRepository
                .findByProtocolInstanceIdIn(pageIds)
                .stream()
                .collect(Collectors.groupingBy(StepInstance::getProtocolInstanceId));
        Map<UUID, Long> devCountByInstance = deviationRepository
                .countDeviationsByProtocolInstanceIdIn(pageIds)
                .stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        List<PatientComplianceDto> results = new ArrayList<>();
        for (ProtocolInstance pi : page.getContent()) {
            List<StepInstance> steps = stepsByInstance.getOrDefault(pi.getId(), List.of());
            long completedCount = steps.stream()
                    .filter(s -> s.getState() == StepState.COMPLETED || s.getState() == StepState.SKIPPED)
                    .count();
            double rate = steps.isEmpty() ? 0.0 : (double) completedCount / steps.size();
            String category = computeCategory(steps);

            if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.equalsIgnoreCase(category)) {
                continue;
            }

            long activeDevs = devCountByInstance.getOrDefault(pi.getId(), 0L);

            results.add(PatientComplianceDto.builder()
                    .patientId(pi.getPatientId())
                    .protocolInstanceId(pi.getId().toString())
                    .protocolCanonical(pi.getProtocolCanonical())
                    .enrolledAt(pi.getEnrolledAt())
                    .status(pi.getStatus().name().toLowerCase())
                    .complianceRate(Math.round(rate * 1000.0) / 10.0)
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
        List<Object[]> rows = complianceEventLogRepository.findPatientsByFacility(facilityId);
        Set<String> patients = new LinkedHashSet<>();
        for (Object[] row : rows) patients.add((String) row[1]);

        // 2 aggregate queries replace findAll() + N+1 per-instance loops
        List<Object[]> stepMetrics = stepInstanceRepository.findProtocolStepMetricsByFacility(facilityId);
        if (stepMetrics.isEmpty()) {
            return FacilitySummaryDto.builder()
                    .facilityId(facilityId)
                    .totalPatients(patients.size())
                    .totalEnrollments(0)
                    .overallComplianceRate(0.0)
                    .protocolBreakdown(List.of())
                    .build();
        }

        List<Object[]> deviationCounts = deviationRepository.findDeviationCountsByFacilityGroupedByProtocol(facilityId);
        Map<String, Long> devsByProtocol = deviationCounts.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));

        long totalCompleted = 0, totalSteps = 0, totalEnrollments = 0;
        List<FacilitySummaryDto.ProtocolBreakdown> breakdowns = new ArrayList<>();

        for (Object[] sm : stepMetrics) {
            String protocolDefId     = (String) sm[0];
            String protocolCanonical = (String) sm[1];
            long enrollments         = toLong(sm[2]);
            long pTotal              = toLong(sm[3]);
            long pCompleted          = toLong(sm[4]);
            long activeDevs          = devsByProtocol.getOrDefault(protocolDefId, 0L);

            totalEnrollments += enrollments;
            totalCompleted   += pCompleted;
            totalSteps       += pTotal;

            double pRate = pTotal > 0 ? Math.round((double) pCompleted / pTotal * 100.0) / 100.0 : 0;
            breakdowns.add(FacilitySummaryDto.ProtocolBreakdown.builder()
                    .protocolDefinitionId(protocolDefId)
                    .protocolCanonical(protocolCanonical)
                    .enrollments(enrollments)
                    .complianceRate(pRate)
                    .activeDeviations(activeDevs)
                    .build());
        }

        double overallRate = totalSteps > 0 ? Math.round((double) totalCompleted / totalSteps * 100.0) / 100.0 : 0;

        return FacilitySummaryDto.builder()
                .facilityId(facilityId)
                .totalPatients(patients.size())
                .totalEnrollments(totalEnrollments)
                .overallComplianceRate(overallRate)
                .protocolBreakdown(breakdowns)
                .build();
    }

    private String computeCategory(List<StepInstance> steps) {
        boolean hasMissed = steps.stream().anyMatch(s -> s.getState() == StepState.MISSED);
        if (hasMissed) return "non_compliant";
        boolean hasOverdue = steps.stream().anyMatch(s -> s.getState() == StepState.OVERDUE);
        if (hasOverdue) return "non_compliant";
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
        List<Object[]> rows = complianceEventLogRepository.findPatientsByFacility(facilityId);
        Set<UUID> ids = new HashSet<>();
        for (Object[] row : rows) {
            ids.add((UUID) row[2]);
        }
        return ids;
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }
}
