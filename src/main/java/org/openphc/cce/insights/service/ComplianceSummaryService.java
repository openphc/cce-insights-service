package org.openphc.cce.insights.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.entity.ProtocolInstance;
import org.openphc.cce.insights.domain.entity.StepInstance;
import org.openphc.cce.insights.domain.enums.StepState;
import org.openphc.cce.insights.domain.enums.CompletionStatus;
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
    private final InboundEventRepository inboundEventRepository;
    private final FacilityDirectory facilityDirectory;

    /** Distinct patients across the district's facilities (null when no district selected). */
    private Set<String> patientsInDistrict(String district) {
        List<String> facilities = facilityDirectory.facilityIdsInDistrict(district);
        if (facilities == null) return null;
        Set<String> patients = new HashSet<>();
        for (String fid : facilities) patients.addAll(protocolInstanceRepository.findPatientIdsAtFacility(fid));
        return patients;
    }

    @Cacheable(value = "analytics",
            key = "'compliance-all-' + (#facilityId ?: 'all') + '-' + (#district ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all') + '-' + #dateFilterMode")
    public ComplianceSummaryDto getAllProtocolsComplianceSummary(String facilityId,
                                                                 String district,
                                                                 OffsetDateTime startDate,
                                                                 OffsetDateTime endDate,
                                                                 String dateFilterMode) {
        boolean hasFacility = facilityId != null && !facilityId.isEmpty();
        LocalDate snapshotDate = endDate != null ? endDate.toLocalDate()
                : startDate != null ? startDate.toLocalDate()
                : null;

        // RI-36 "eventTime" (Clinical Event Date) mode — the patient block (tracked/compliant/rate) is
        // the matched-event cohort by clinical event_time, identical to the Dashboard "Service
        // Compliance" card, so the two reconcile exactly. NB: this is the CLINICAL event clock, NOT
        // the system updated_at "activity" shown by the patient details log. Step metrics + deviation
        // breakdown keep their existing sources (protocol-step snapshot + occurrence-date deviations),
        // which are cohort-independent.
        if ("eventTime".equalsIgnoreCase(dateFilterMode)) {
            return buildAllProtocolsEventTimeSummary(facilityId, district, startDate, endDate, snapshotDate, hasFacility);
        }
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
            DeviationCounts dev = occurrenceDeviations(facilityId, startDate, endDate);
            long effectiveEnrollments = enrolledInPeriod >= 0 ? enrolledInPeriod : totalEnrollments;
            // Compliant/non-compliant on the CLINICAL clock when a date range is set: non-compliant =
            // cohort patients (enrolled in window) that have a deviation whose OCCURRENCE date is in the
            // window; compliant = cohort − non-compliant. This replaces the mv_daily_compliance_kpis
            // as-of, cumulative-deviation count (which double-counted historical deviations and was only
            // reconciled to the cohort via a Math.min clamp). With no date range we keep the MV snapshot.
            long effectiveCompliant;
            if (enrolledInPeriod >= 0) {
                long nonCompliant = deviationRepository.countDistinctPatientsWithDeviationsBetween(startDate, endDate);
                effectiveCompliant = Math.max(0, effectiveEnrollments - nonCompliant);
            } else {
                effectiveCompliant = toLong(kpis[10]);   // no date filter → current-state snapshot
            }
            return ComplianceSummaryDto.builder()
                    .totalEnrollments(effectiveEnrollments)
                    .compliantPatients(effectiveCompliant)
                    .complianceRate(effectiveEnrollments > 0
                            ? Math.round((double) effectiveCompliant / effectiveEnrollments * 1000.0) / 10.0
                            : 0.0)
                    .stepMetrics(stepMetricsFrom(kpis))
                    // Deviations from mv_daily_deviation_kpis (clinical OCCURRENCE date, in-window) —
                    // same source/clock as the Deviations page, so the two reconcile. (The compliance
                    // state snapshot's kpis[11..14] counted deviations cumulatively as-of the snapshot
                    // day, which disagreed with the Deviations page; those indices are no longer used.)
                    .deviationCount(dev.total())
                    .deviationBreakdown(Map.of(
                            "overdue",        dev.overdue(),
                            "missed",         dev.missed(),
                            "orderViolation", dev.orderViolation()))
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

        // Compliant/non-compliant on the CLINICAL clock: non-compliant = facility cohort patients
        // (enrolled in window) with a deviation whose OCCURRENCE date is in the window; compliant =
        // cohort − non-compliant. Replaces dm[0] (an all-time facility count that needed a Math.min
        // clamp). With no date filter we keep the all-time snapshot.
        long compliantPatients;
        if (startDate != null || endDate != null) {
            long nonCompliant = deviationRepository.countDistinctPatientsWithDeviationsBetween(
                    facilityId, startDate, endDate);
            compliantPatients = Math.max(0, totalEnrollments - nonCompliant);
        } else {
            compliantPatients = Math.min(totalEnrollments, toLong(dm[0]));
        }
        double complianceRate  = (double) compliantPatients / totalEnrollments;
        DeviationCounts dev = occurrenceDeviations(facilityId, startDate, endDate);

        return ComplianceSummaryDto.builder()
                .totalEnrollments(totalEnrollments)
                .compliantPatients(compliantPatients)
                .complianceRate(Math.round(complianceRate * 1000.0) / 10.0)
                .stepMetrics(stepMetricsFrom(sm))
                // Deviations from mv_daily_deviation_kpis (clinical occurrence date, in-window) —
                // reconciles with the Deviations page; dm[1..4] (all-time base aggregate) no longer used.
                .deviationCount(dev.total())
                .deviationBreakdown(Map.of(
                        "overdue",        dev.overdue(),
                        "missed",         dev.missed(),
                        "orderViolation", dev.orderViolation()))
                .build();
    }

    /**
     * RI-36 all-protocols "eventTime" (Clinical Event Date) summary — patient block from the
     * matched-event cohort (by clinical event_time), the SAME queries as
     * {@link DashboardService#getComplianceSummary}, so this reconciles with the Dashboard card.
     * (Clinical event clock, NOT the system updated_at "activity" detail log.) Step metrics keep their
     * existing snapshot source (MV for all-facilities, base aggregate for a single facility) and the
     * deviation breakdown uses occurrence-date counts — both cohort-independent, shared with the
     * enrollment path.
     */
    private ComplianceSummaryDto buildAllProtocolsEventTimeSummary(String facilityId,
                                                                   String district,
                                                                   OffsetDateTime startDate,
                                                                   OffsetDateTime endDate,
                                                                   LocalDate snapshotDate,
                                                                   boolean hasFacility) {
        long tracked = inboundEventRepository.countDistinctPatientsWithMatchedEvents(
                facilityId, district, startDate, endDate);
        if (tracked == 0) {
            return ComplianceSummaryDto.builder()
                    .totalEnrollments(0).compliantPatients(0).complianceRate(0.0)
                    .stepMetrics(ComplianceSummaryDto.StepMetrics.builder().build())
                    .deviationCount(0).deviationBreakdown(Map.of())
                    .build();
        }
        long nonCompliant = deviationRepository.countDistinctNonCompliantAmongMatched(
                facilityId, district, startDate, endDate);
        long compliant = Math.max(0, tracked - nonCompliant);
        Object[] stepArr = hasFacility
                ? stepInstanceRepository.aggregateStepMetricsByFacility(facilityId)
                : dailyKpiRepository.getComplianceKpisAll(snapshotDate);
        DeviationCounts dev = occurrenceDeviations(facilityId, startDate, endDate);
        return ComplianceSummaryDto.builder()
                .totalEnrollments(tracked)
                .compliantPatients(compliant)
                .complianceRate(Math.round((double) compliant / tracked * 1000.0) / 10.0)
                .stepMetrics(stepMetricsFrom(stepArr))
                .deviationCount(dev.total())
                .deviationBreakdown(Map.of(
                        "overdue",        dev.overdue(),
                        "missed",         dev.missed(),
                        "orderViolation", dev.orderViolation()))
                .build();
    }

    /** Maps a step-metric aggregate row (from mv_daily_compliance_kpis or aggregateStepMetricsByFacility,
     *  same column order) into a StepMetrics DTO. */
    private ComplianceSummaryDto.StepMetrics stepMetricsFrom(Object[] a) {
        return ComplianceSummaryDto.StepMetrics.builder()
                .totalSteps(toLong(a[8])).completed(toLong(a[0]))
                .onTime(toLong(a[6])).late(toLong(a[7])).early(toLong(a[5]))
                .overdue(toLong(a[1])).missed(toLong(a[2])).due(toLong(a[3])).pending(toLong(a[4]))
                .build();
    }

    /** Immutable holder for occurrence-in-window deviation counts (total + by-type). */
    private record DeviationCounts(long total, long overdue, long missed, long orderViolation) {}

    /**
     * Deviation counts by CLINICAL occurrence date within [startDate, endDate], read from
     * mv_daily_deviation_kpis via {@link DeviationRepository#countByTypeFiltered} — the SAME
     * source and clock as the Deviations page, so the compliance-page deviation card reconciles
     * with it. Null dates widen to all-time. Passing {@code null} protocol = all protocols.
     */
    private DeviationCounts occurrenceDeviations(String facilityId,
                                                 OffsetDateTime startDate, OffsetDateTime endDate) {
        long overdue = 0, missed = 0, orderViolation = 0, total = 0;
        for (Object[] row : deviationRepository.countByTypeFiltered(null, facilityId, startDate, endDate)) {
            long cnt = toLong(row[1]);
            total += cnt;
            switch ((String) row[0]) {
                case "OVERDUE":         overdue = cnt;        break;
                case "MISSED":          missed = cnt;         break;
                case "ORDER_VIOLATION": orderViolation = cnt; break;
                default: /* other/unknown types still counted in total */ break;
            }
        }
        return new DeviationCounts(total, overdue, missed, orderViolation);
    }

    @Cacheable(value = "analytics",
            key = "'compliance-' + #protocolDefinitionId + '-' + (#facilityId ?: 'all') + '-' + (#district ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all') + '-' + #dateFilterMode")
    public ComplianceSummaryDto getProtocolComplianceSummary(UUID protocolDefinitionId, String facilityId,
                                                              String district,
                                                              OffsetDateTime startDate,
                                                              OffsetDateTime endDate,
                                                              String dateFilterMode) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        // WINDOWED semantics for every card, on the cohort selected by dateFilterMode:
        // "enrollment" = patients ENROLLED within [startDate, endDate]; "eventTime" / Clinical Event
        // Date (RI-36) = patients with a protocol-MATCHED inbound event by clinical event_time in range
        // (reconciles with the Dashboard card; NOT the system updated_at "activity" detail log).
        // Optionally narrowed to the selected facility via
        // mv_patient_facility_latest. Every card (patient counts, transactions, deviations) is derived
        // from ONE scoped instance set, so they stay mutually consistent and all honour the range.
        // (The previous code mixed an MV daily-snapshot for All-Facilities steps with un-scoped
        // all-time facility aggregates, which never agreed.)
        List<ProtocolInstance> scoped = latestInstancePerPatient(
                loadInstancesForPatientFilter(protocolDefinitionId, null, startDate, endDate, dateFilterMode));
        if (hasFacility) {
            Set<String> patientsAtFacility = new HashSet<>(
                    protocolInstanceRepository.findPatientIdsAtFacility(facilityId));
            scoped = scoped.stream()
                    .filter(pi -> patientsAtFacility.contains(pi.getPatientId()))
                    .collect(Collectors.toList());
        }
        // Global district scope — keep only patients assigned to a facility in the district.
        Set<String> districtPatients = patientsInDistrict(district);
        if (districtPatients != null) {
            scoped = scoped.stream()
                    .filter(pi -> districtPatients.contains(pi.getPatientId()))
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

        // Deviations — same instances, counted on their CLINICAL OCCURRENCE date within
        // [startDate, endDate] (occurredAt(), not system detected_at), consistent with the windowed
        // enrollment scoping AND with mv_daily_deviation_kpis / the Deviations page (same clock).
        List<Object[]> devRows = deviationRepository.findDeviationTypesByInstanceIdIn(
                instanceIds, startDate, endDate);
        Map<UUID, Long> devCountByInstance = devRows.stream()
                .collect(Collectors.groupingBy(r -> (UUID) r[0], Collectors.counting()));
        long overdueDevs = devRows.stream().filter(r -> "OVERDUE".equals(r[1])).count();
        long missedDevs  = devRows.stream().filter(r -> "MISSED".equals(r[1])).count();
        long orderDevs   = devRows.stream().filter(r -> "ORDER_VIOLATION".equals(r[1])).count();

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
                .deviationCount((long) devRows.size())
                .deviationBreakdown(Map.of(
                        "overdue",        overdueDevs,
                        "missed",         missedDevs,
                        "orderViolation", orderDevs))
                .build();
    }

    @Cacheable(value = "analytics",
            key = "'protocol-patients-' + #protocolDefinitionId + '-' + #statusFilter + '-' + (#facilityIdFilter ?: 'all') + '-' + (#district ?: 'all') + '-' + #patientIdFilter + '-' + #startDate + '-' + #endDate + '-' + #dateFilterMode + '-' + #limit + '-' + #offset")
    public ProtocolPatientsPage getProtocolPatients(UUID protocolDefinitionId, String statusFilter,
                                                    String facilityIdFilter,
                                                    String district,
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

        // Global district scope.
        Set<String> districtPatients = patientsInDistrict(district);
        if (districtPatients != null) {
            instances = instances.stream()
                    .filter(pi -> districtPatients.contains(pi.getPatientId()))
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
        // "eventTime" (Clinical Event Date) = scope by clinical event_time of a protocol-matched event;
        // NOT the system updated_at "activity" detail log.
        boolean eventTimeMode = "eventTime".equalsIgnoreCase(dateFilterMode);
        List<ProtocolInstance> instances;
        if (!hasDateRange) {
            instances = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId);
        } else if (eventTimeMode) {
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
