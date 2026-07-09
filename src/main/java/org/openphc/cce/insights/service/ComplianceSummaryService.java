package org.openphc.cce.insights.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.entity.Deviation;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.enums.CompletionStatus;
import org.openphc.cce.insights.domain.enums.DeviationType;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.ComplianceSummaryDto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.openphc.cce.insights.web.dto.FacilitySummaryDto;
import org.openphc.cce.insights.web.dto.PatientComplianceDto;
import org.openphc.cce.insights.web.dto.ProtocolPatientsPage;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

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

    @Cacheable(value = "analytics",
            key = "'compliance-all-' + (#facilityId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public ComplianceSummaryDto getAllProtocolsComplianceSummary(String facilityId,
                                                                 OffsetDateTime startDate,
                                                                 OffsetDateTime endDate) {
        boolean hasFacility = facilityId != null && !facilityId.isEmpty();
        LocalDate snapshotDate = endDate != null ? endDate.toLocalDate()
                : startDate != null ? startDate.toLocalDate()
                : null;
        long enrolledInPeriod = (startDate != null || endDate != null)
                ? (hasFacility
                        ? protocolInstanceRepository.countDistinctPatientsForFacility(facilityId, startDate, endDate)
                        : protocolInstanceRepository.countDistinctPatientsEnrolledBetween(startDate, endDate))
                : -1L;

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
            long effectiveEnrollments = enrolledInPeriod >= 0 ? enrolledInPeriod : totalEnrollments;
            long effectiveCompliant = enrolledInPeriod >= 0
                    ? Math.min(effectiveEnrollments, compliantPatients)
                    : compliantPatients;
            return ComplianceSummaryDto.builder()
                    .totalEnrollments(effectiveEnrollments)
                    .compliantPatients(effectiveCompliant)
                    .complianceRate(effectiveEnrollments > 0
                            ? Math.round((double) effectiveCompliant / effectiveEnrollments * 1000.0) / 10.0
                            : 0.0)
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

        long totalEnrollments = enrolledInPeriod >= 0 ? enrolledInPeriod : toLong(sm[9]);
        if (totalEnrollments == 0) {
            return ComplianceSummaryDto.builder()
                    .totalEnrollments(0).compliantPatients(0).complianceRate(0.0)
                    .stepMetrics(ComplianceSummaryDto.StepMetrics.builder().build())
                    .deviationCount(0).deviationBreakdown(Map.of())
                    .build();
        }

        // Clamp compliant to enrollments: dm[0] is an all-time facility deviation-instance count and
        // can exceed the period-scoped facility enrollment (totalEnrollments), which otherwise yields
        // rates > 100% and negative non-compliant counts (mirrors the no-facility branch's Math.min).
        long compliantPatients = Math.min(totalEnrollments, toLong(dm[0]));
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

    @Cacheable(value = "analytics",
            key = "'compliance-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public ComplianceSummaryDto getProtocolComplianceSummary(UUID protocolDefinitionId, String facilityId,
                                                              OffsetDateTime startDate,
                                                              OffsetDateTime endDate) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        // WINDOWED semantics for every card: patients ENROLLED within [startDate, endDate] (across
        // all facilities, or the selected facility via mv_patient_facility_latest), plus their step
        // state and the deviations detected within the range. Every card is derived from ONE scoped
        // instance set, so the patient counts, transactions, and deviation cards are mutually
        // consistent and all honour the date range. (The previous code mixed an MV daily-snapshot for
        // All-Facilities steps with un-scoped all-time facility aggregates, which never agreed.)
        List<ProtocolInstance> scoped = latestInstancePerPatient(
                loadInstancesForPatientFilter(protocolDefinitionId, null, startDate, endDate, "enrollment"));
        if (hasFacility) {
            Set<String> patientsAtFacility = new HashSet<>(
                    protocolInstanceRepository.findPatientIdsAtFacility(facilityId));
            scoped = scoped.stream()
                    .filter(pi -> patientsAtFacility.contains(pi.getPatientId()))
                    .collect(Collectors.toList());
        }
        long totalEnrollments = scoped.size();   // one row per patient (latest enrollment)
        if (totalEnrollments == 0) {
            return buildEmptySummary(pd);
        }
        List<UUID> instanceIds = scoped.stream().map(ProtocolInstance::getId).collect(Collectors.toList());

        // Step metrics — aggregated from the scoped instances' steps.
        List<StepInstance> steps = stepInstanceRepository.findByProtocolInstanceIdIn(instanceIds);
        long stepCompleted = steps.stream().filter(s -> s.getState() == StepState.COMPLETED || s.getState() == StepState.SKIPPED).count();
        long stepOverdue   = steps.stream().filter(s -> s.getState() == StepState.OVERDUE).count();
        long stepMissed    = steps.stream().filter(s -> s.getState() == StepState.MISSED).count();
        long stepDue       = steps.stream().filter(s -> s.getState() == StepState.DUE).count();
        long stepPending   = steps.stream().filter(s -> s.getState() == StepState.PENDING).count();
        long stepEarly     = steps.stream().filter(s -> s.getCompletionStatus() == CompletionStatus.EARLY).count();
        long stepOnTime    = steps.stream().filter(s -> s.getCompletionStatus() == CompletionStatus.ON_TIME).count();
        long stepLate      = steps.stream().filter(s -> s.getCompletionStatus() == CompletionStatus.LATE).count();
        long stepTotal     = steps.size();

        // Deviations — same instances, detected WITHIN [startDate, endDate] (windowed), consistent
        // with the windowed enrollment scoping of the patient set.
        List<Deviation> devs = deviationRepository.findByProtocolInstanceIdIn(instanceIds).stream()
                .filter(d -> inRange(d.getDetectedAt(), startDate, endDate))
                .collect(Collectors.toList());
        Map<UUID, Long> devCountByInstance = devs.stream()
                .collect(Collectors.groupingBy(Deviation::getProtocolInstanceId, Collectors.counting()));
        long overdueDevs = devs.stream().filter(d -> d.getDeviationType() == DeviationType.OVERDUE).count();
        long missedDevs  = devs.stream().filter(d -> d.getDeviationType() == DeviationType.MISSED).count();
        long orderDevs   = devs.stream().filter(d -> d.getDeviationType() == DeviationType.ORDER_VIOLATION).count();

        // Patient compliance: compliant = distinct patients (scoped, one instance each) with zero
        // deviations in the period -> compliant <= tracked by construction (rate bounded).
        long compliantPatients = scoped.stream()
                .filter(pi -> devCountByInstance.getOrDefault(pi.getId(), 0L) == 0L)
                .count();
        double complianceRate = (double) compliantPatients / totalEnrollments;

        Map<String, Long> statusBreakdown = scoped.stream()
                .collect(Collectors.groupingBy(pi -> pi.getStatus().name().toLowerCase(), Collectors.counting()));

        return ComplianceSummaryDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalEnrollments(totalEnrollments)
                .compliantPatients(compliantPatients)
                .statusBreakdown(statusBreakdown)
                .complianceRate(Math.round(complianceRate * 1000.0) / 10.0)
                .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                        .totalSteps(stepTotal).completed(stepCompleted)
                        .onTime(stepOnTime).late(stepLate).early(stepEarly)
                        .overdue(stepOverdue).missed(stepMissed).due(stepDue).pending(stepPending)
                        .build())
                .deviationCount((long) devs.size())
                .deviationBreakdown(Map.of(
                        "overdue",        overdueDevs,
                        "missed",         missedDevs,
                        "orderViolation", orderDevs))
                .build();
    }

    @Cacheable(value = "analytics",
            key = "'protocol-patients-' + #protocolDefinitionId + '-' + #statusFilter + '-' + (#facilityIdFilter ?: 'all') + '-' + #patientIdFilter + '-' + #startDate + '-' + #endDate + '-' + #dateFilterMode + '-' + #limit + '-' + #offset")
    public ProtocolPatientsPage getProtocolPatients(UUID protocolDefinitionId, String statusFilter,
                                                    String facilityIdFilter,
                                                    String patientIdFilter,
                                                    OffsetDateTime startDate, OffsetDateTime endDate,
                                                    String dateFilterMode,
                                                    int limit, int offset) {
        protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        int pageSize = Math.max(limit, 1);
        List<ProtocolInstance> instances = latestInstancePerPatient(
                loadInstancesForPatientFilter(protocolDefinitionId, patientIdFilter, startDate, endDate, dateFilterMode));

        // Apply facility filter (membership via mv_patient_facility_latest).
        if (facilityIdFilter != null && !facilityIdFilter.isEmpty()) {
            Set<String> patientIdsAtFacility = complianceEventLogRepository
                    .findPatientsByFacility(facilityIdFilter)
                    .stream()
                    .map(r -> (String) r[1])
                    .collect(Collectors.toSet());
            instances = instances.stream()
                    .filter(pi -> patientIdsAtFacility.contains(pi.getPatientId()))
                    .collect(Collectors.toList());
        }

        instances.sort(Comparator.comparing(ProtocolInstance::getEnrolledAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<PatientComplianceDto> matching = buildPatientComplianceDtos(
                instances, statusFilter, startDate, endDate);
        long total = matching.size();
        int from = Math.min(offset, matching.size());
        int to = Math.min(offset + pageSize, matching.size());
        return new ProtocolPatientsPage(matching.subList(from, to), total);
    }

    /** One row per patient — keep the most recent enrollment in the filtered set. */
    private static List<ProtocolInstance> latestInstancePerPatient(List<ProtocolInstance> instances) {
        Map<String, ProtocolInstance> latest = new LinkedHashMap<>();
        for (ProtocolInstance pi : instances) {
            if (pi.getPatientId() == null) continue;
            latest.merge(pi.getPatientId(), pi, (existing, candidate) -> {
                if (existing.getEnrolledAt() == null) return candidate;
                if (candidate.getEnrolledAt() == null) return existing;
                return existing.getEnrolledAt().isAfter(candidate.getEnrolledAt()) ? existing : candidate;
            });
        }
        return new ArrayList<>(latest.values());
    }

    private List<ProtocolInstance> loadInstancesForPatientFilter(UUID protocolDefinitionId,
                                                                  String patientIdFilter,
                                                                  OffsetDateTime startDate,
                                                                  OffsetDateTime endDate,
                                                                  String dateFilterMode) {
        boolean hasDateRange = startDate != null || endDate != null;
        boolean activityMode = "activity".equalsIgnoreCase(dateFilterMode);
        List<ProtocolInstance> instances;
        if (!hasDateRange) {
            instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        } else if (activityMode) {
            instances = protocolInstanceRepository.findByProtocolDefinitionIdWithActivityBetween(
                    protocolDefinitionId, startDate, endDate);
        } else {
            instances = protocolInstanceRepository.findByProtocolDefinitionIdAndEnrolledBetween(
                    protocolDefinitionId, startDate, endDate);
        }
        if (patientIdFilter != null && !patientIdFilter.isEmpty()) {
            String pattern = patientIdFilter.toLowerCase();
            return instances.stream()
                    .filter(pi -> pi.getPatientId() != null
                            && pi.getPatientId().toLowerCase().contains(pattern))
                    .collect(Collectors.toList());
        }
        return instances;
    }

    private List<PatientComplianceDto> buildPatientComplianceDtos(List<ProtocolInstance> instances,
                                                                   String statusFilter,
                                                                   OffsetDateTime startDate,
                                                                   OffsetDateTime endDate) {
        if (instances.isEmpty()) {
            return List.of();
        }

        List<UUID> instanceIds = instances.stream().map(ProtocolInstance::getId).collect(Collectors.toList());
        Map<UUID, List<StepInstance>> stepsByInstance = stepInstanceRepository
                .findByProtocolInstanceIdIn(instanceIds)
                .stream()
                .collect(Collectors.groupingBy(StepInstance::getProtocolInstanceId));
        Map<UUID, Long> devCountByInstance = deviationRepository
                .countDeviationsByProtocolInstanceIdIn(instanceIds, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        List<PatientComplianceDto> results = new ArrayList<>();
        for (ProtocolInstance pi : instances) {
            List<StepInstance> steps = stepsByInstance.getOrDefault(pi.getId(), List.of());
            long completedCount = steps.stream()
                    .filter(s -> s.getState() == StepState.COMPLETED || s.getState() == StepState.SKIPPED)
                    .count();
            double rate = steps.isEmpty() ? 0.0 : (double) completedCount / steps.size();
            long activeDevs = devCountByInstance.getOrDefault(pi.getId(), 0L);
            String category = computeCategory(activeDevs);

            if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.equalsIgnoreCase(category)) {
                continue;
            }

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

    /** Inclusive [start,end] membership; null bounds are treated as open (used to date-scope deviations). */
    private static boolean inRange(OffsetDateTime t, OffsetDateTime start, OffsetDateTime end) {
        if (t == null) return false;
        if (start != null && t.isBefore(start)) return false;
        if (end != null && t.isAfter(end)) return false;
        return true;
    }

    @Cacheable(value = "analytics",
            key = "'facility-' + #facilityId + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public FacilitySummaryDto getFacilityComplianceSummary(String facilityId,
                                                           OffsetDateTime startDate,
                                                           OffsetDateTime endDate) {
        // totalPatients respects the global date range so the tile lines up with the
        // Dashboard tracked-cohort numbers.
        long totalPatients = (startDate != null || endDate != null)
                ? protocolInstanceRepository.countDistinctPatientsForFacility(facilityId, startDate, endDate)
                : complianceEventLogRepository.findPatientsByFacility(facilityId)
                        .stream().map(r -> (String) r[1]).distinct().count();

        // 2 aggregate queries replace findAll() + N+1 per-instance loops
        List<Object[]> stepMetrics = stepInstanceRepository.findProtocolStepMetricsByFacility(facilityId);
        if (stepMetrics.isEmpty()) {
            return FacilitySummaryDto.builder()
                    .facilityId(facilityId)
                    .totalPatients(totalPatients)
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
                .totalPatients(totalPatients)
                .totalEnrollments(totalEnrollments)
                .overallComplianceRate(overallRate)
                .protocolBreakdown(breakdowns)
                .build();
    }

    /**
     * Matches {@link DashboardService#getComplianceSummary}: a patient is compliant only when
     * they have no deviation records (optionally scoped to the same date range).
     */
    private static String computeCategory(long deviationCount) {
        return deviationCount > 0 ? "non_compliant" : "on_track";
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

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }
}
