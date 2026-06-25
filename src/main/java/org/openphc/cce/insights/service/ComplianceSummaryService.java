package org.openphc.cce.insights.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.ComplianceSummaryDto;

import java.time.LocalDate;
import org.openphc.cce.insights.web.dto.FacilitySummaryDto;
import org.openphc.cce.insights.web.dto.PatientComplianceDto;
import org.openphc.cce.insights.web.dto.ProtocolPatientsPage;
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
    private final DailyKpiRepository dailyKpiRepository;

    @Cacheable(value = "analytics", key = "'compliance-all-' + (#facilityId ?: 'all') + '-' + (#snapshotDate ?: 'today')")
    public ComplianceSummaryDto getAllProtocolsComplianceSummary(String facilityId, LocalDate snapshotDate) {
        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        if (!hasFacility) {
            // No facility filter — use pre-aggregated MV (replaces 2 full base-table scans)
            Object[] kpis = dailyKpiRepository.getComplianceKpisAll(snapshotDate);
            long totalEnrollments = toLong(kpis[9]);
            if (totalEnrollments == 0) {
                return ComplianceSummaryDto.builder()
                        .totalEnrollments(0).compliantPatients(0).complianceRate(0.0)
                        .stepMetrics(ComplianceSummaryDto.StepMetrics.builder().build())
                        .deviationCount(0).deviationBreakdown(Map.of())
                        .build();
            }
            long compliantPatients = toLong(kpis[10]);
            return ComplianceSummaryDto.builder()
                    .totalEnrollments(totalEnrollments)
                    .compliantPatients(compliantPatients)
                    .complianceRate(Math.round((double) compliantPatients / totalEnrollments * 1000.0) / 10.0)
                    .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                            .totalSteps(toLong(kpis[8])).completed(toLong(kpis[0]))
                            .onTime(toLong(kpis[6])).late(toLong(kpis[7])).early(toLong(kpis[5]))
                            .overdue(toLong(kpis[1])).missed(toLong(kpis[2])).due(toLong(kpis[3])).pending(toLong(kpis[4]))
                            .build())
                    .deviationCount(toLong(kpis[11]))
                    .deviationBreakdown(Map.of(
                            "overdue",        toLong(kpis[12]),
                            "missed",         toLong(kpis[13]),
                            "orderViolation", toLong(kpis[14])))
                    .build();
        }

        // facilityId provided — mv_daily_compliance_kpis has no facility dimension, fall back to base tables
        Object[] sm = stepInstanceRepository.aggregateStepMetricsByFacility(facilityId);
        Object[] dm = deviationRepository.aggregateDeviationMetricsByFacility(facilityId);

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
                .complianceRate(Math.round(complianceRate * 1000.0) / 10.0)
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

    @Cacheable(value = "analytics", key = "'compliance-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all') + '-' + (#snapshotDate ?: 'today')")
    public ComplianceSummaryDto getProtocolComplianceSummary(UUID protocolDefinitionId, String facilityId,
                                                              LocalDate snapshotDate) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        if (!hasFacility) {
            // No facility filter — use pre-aggregated MV. Replaces 3 base-table queries:
            // aggregateStepMetrics, aggregateDeviationMetrics, findByProtocolDefinitionId (statusBreakdown)
            // snapshotDate: null = today's snapshot; past date = historical snapshot for that day
            Object[] kpis = dailyKpiRepository.getComplianceKpisByProtocol(protocolDefinitionId, snapshotDate);
            long totalEnrollments = toLong(kpis[9]);
            if (totalEnrollments == 0) {
                return buildEmptySummary(pd);
            }
            // Status breakdown comes from MV columns — no separate load needed
            Map<String, Long> statusBreakdown = new LinkedHashMap<>();
            statusBreakdown.put("active",    toLong(kpis[15]));
            statusBreakdown.put("completed", toLong(kpis[16]));
            statusBreakdown.put("withdrawn", toLong(kpis[17]));
            statusBreakdown.put("expired",   toLong(kpis[18]));

            long compliantPatients = toLong(kpis[10]);
            return ComplianceSummaryDto.builder()
                    .protocolDefinitionId(protocolDefinitionId)
                    .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                    .totalEnrollments(totalEnrollments)
                    .compliantPatients(compliantPatients)
                    .statusBreakdown(statusBreakdown)
                    .complianceRate(Math.round((double) compliantPatients / totalEnrollments * 1000.0) / 10.0)
                    .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                            .totalSteps(toLong(kpis[8])).completed(toLong(kpis[0]))
                            .onTime(toLong(kpis[6])).late(toLong(kpis[7])).early(toLong(kpis[5]))
                            .overdue(toLong(kpis[1])).missed(toLong(kpis[2])).due(toLong(kpis[3])).pending(toLong(kpis[4]))
                            .build())
                    .deviationCount(toLong(kpis[11]))
                    .deviationBreakdown(Map.of(
                            "overdue",        toLong(kpis[12]),
                            "missed",         toLong(kpis[13]),
                            "orderViolation", toLong(kpis[14])))
                    .build();
        }

        // facilityId provided — mv_daily_compliance_kpis has no facility dimension, fall back to base tables
        Object[] sm = stepInstanceRepository.aggregateStepMetricsByProtocolAndFacility(protocolDefinitionId, facilityId);
        Object[] dm = deviationRepository.aggregateDeviationMetricsByProtocolAndFacility(protocolDefinitionId, facilityId);

        long totalEnrollments = toLong(sm[9]);
        if (totalEnrollments == 0) {
            return buildEmptySummary(pd);
        }

        // Status breakdown still needs the instance list for the facility-filtered case
        Set<UUID> facilityInstanceIds = getFacilityInstanceIds(facilityId);
        List<ProtocolInstance> instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId)
                .stream()
                .filter(pi -> facilityInstanceIds.contains(pi.getId()))
                .collect(Collectors.toList());
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
                .complianceRate(Math.round(complianceRate * 1000.0) / 10.0)
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
    public ProtocolPatientsPage getProtocolPatients(UUID protocolDefinitionId, String statusFilter, String patientIdFilter, int limit, int offset) {
        protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        int pageSize = Math.max(limit, 1);

        if (statusFilter != null && !statusFilter.isEmpty()) {
            List<ProtocolInstance> instances = loadInstancesForPatientFilter(protocolDefinitionId, patientIdFilter);
            instances.sort(Comparator.comparing(ProtocolInstance::getEnrolledAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));

            List<PatientComplianceDto> matching = buildPatientComplianceDtos(instances, statusFilter);
            long total = matching.size();
            int from = Math.min(offset, matching.size());
            int to = Math.min(offset + pageSize, matching.size());
            return new ProtocolPatientsPage(matching.subList(from, to), total);
        }

        Pageable pageable = PageRequest.of(offset / pageSize, pageSize, Sort.by(Sort.Direction.DESC, "enrolledAt"));
        Page<ProtocolInstance> page;
        if (patientIdFilter != null && !patientIdFilter.isEmpty()) {
            page = protocolInstanceRepository.findByProtocolDefinitionIdAndPatientIdContaining(
                    protocolDefinitionId, patientIdFilter, pageable);
        } else {
            page = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId, pageable);
        }

        return new ProtocolPatientsPage(buildPatientComplianceDtos(page.getContent(), null), page.getTotalElements());
    }

    private List<ProtocolInstance> loadInstancesForPatientFilter(UUID protocolDefinitionId, String patientIdFilter) {
        if (patientIdFilter != null && !patientIdFilter.isEmpty()) {
            String pattern = patientIdFilter.toLowerCase();
            return protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId).stream()
                    .filter(pi -> pi.getPatientId() != null
                            && pi.getPatientId().toLowerCase().contains(pattern))
                    .collect(Collectors.toList());
        }
        return protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
    }

    private List<PatientComplianceDto> buildPatientComplianceDtos(List<ProtocolInstance> instances, String statusFilter) {
        if (instances.isEmpty()) {
            return List.of();
        }

        List<UUID> instanceIds = instances.stream().map(ProtocolInstance::getId).collect(Collectors.toList());
        Map<UUID, List<StepInstance>> stepsByInstance = stepInstanceRepository
                .findByProtocolInstanceIdIn(instanceIds)
                .stream()
                .collect(Collectors.groupingBy(StepInstance::getProtocolInstanceId));
        Map<UUID, Long> devCountByInstance = deviationRepository
                .countDeviationsByProtocolInstanceIdIn(instanceIds)
                .stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        List<PatientComplianceDto> results = new ArrayList<>();
        for (ProtocolInstance pi : instances) {
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

            double pRate = pTotal > 0 ? Math.round((double) pCompleted / pTotal * 1000.0) / 10.0 : 0;
            breakdowns.add(FacilitySummaryDto.ProtocolBreakdown.builder()
                    .protocolDefinitionId(protocolDefId)
                    .protocolCanonical(protocolCanonical)
                    .enrollments(enrollments)
                    .complianceRate(pRate)
                    .activeDeviations(activeDevs)
                    .build());
        }

        double overallRate = totalSteps > 0 ? Math.round((double) totalCompleted / totalSteps * 1000.0) / 10.0 : 0;

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
