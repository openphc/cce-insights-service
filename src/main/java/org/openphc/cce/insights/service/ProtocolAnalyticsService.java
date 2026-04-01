package org.openphc.cce.insights.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.ProtocolDefinition;
import org.openphc.cce.insights.domain.repository.*;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProtocolAnalyticsService {

    private final ProtocolDefinitionRepository protocolDefinitionRepository;
    private final ProtocolInstanceRepository protocolInstanceRepository;
    private final StepInstanceRepository stepInstanceRepository;

    @Cacheable(value = "analytics", key = "'step-analytics-' + #protocolDefinitionId")
    public StepAnalyticsDto getStepAnalytics(UUID protocolDefinitionId) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<Object[]> rows = stepInstanceRepository.findStepAnalytics(protocolDefinitionId);

        List<StepAnalyticsDto.StepMetric> steps = rows.stream().map(row -> {
            long totalInst = ((Number) row[1]).longValue();
            long completedCount = ((Number) row[2]).longValue();
            double completionRate = totalInst > 0 ? Math.round((double) completedCount / totalInst * 100.0) / 100.0 : 0;

            return StepAnalyticsDto.StepMetric.builder()
                    .actionId((String) row[0])
                    .totalInstances(totalInst)
                    .completedCount(completedCount)
                    .completionRate(completionRate)
                    .timelinessDistribution(StepAnalyticsDto.TimelinessDistribution.builder()
                            .early(((Number) row[3]).longValue())
                            .onTime(((Number) row[4]).longValue())
                            .late(((Number) row[5]).longValue())
                            .build())
                    .overdueCount(((Number) row[6]).longValue())
                    .missedCount(((Number) row[7]).longValue())
                    .skippedCount(((Number) row[8]).longValue())
                    .pendingCount(((Number) row[9]).longValue())
                    .avgDaysToComplete(row[10] != null ? ((Number) row[10]).doubleValue() : null)
                    .medianDaysToComplete(row[11] != null ? ((Number) row[11]).doubleValue() : null)
                    .build();
        }).collect(Collectors.toList());

        return StepAnalyticsDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .steps(steps)
                .build();
    }

    @Cacheable(value = "analytics", key = "'funnel-' + #protocolDefinitionId")
    public CompletionFunnelDto getCompletionFunnel(UUID protocolDefinitionId) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<Object[]> rows = stepInstanceRepository.findCompletionFunnel(protocolDefinitionId);
        long totalEnrollments = protocolInstanceRepository.findByProtocolDefinitionId(protocolDefinitionId).size();

        List<CompletionFunnelDto.FunnelStep> funnel = new ArrayList<>();
        int order = 1;
        for (Object[] row : rows) {
            long reached = ((Number) row[1]).longValue();
            long completed = ((Number) row[2]).longValue();
            double completionRate = reached > 0 ? Math.round((double) completed / reached * 100.0) / 100.0 : 0;
            double dropOff = reached > 0 ? Math.round((1.0 - (double) completed / reached) * 100.0) / 100.0 : 0;

            funnel.add(CompletionFunnelDto.FunnelStep.builder()
                    .actionId((String) row[0])
                    .stepOrder(order++)
                    .reachedCount(reached)
                    .completedCount(completed)
                    .completionRate(completionRate)
                    .dropOffRate(dropOff)
                    .build());
        }

        return CompletionFunnelDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalEnrollments(totalEnrollments)
                .funnel(funnel)
                .build();
    }

    @Cacheable(value = "analytics", key = "'outcome-' + #protocolDefinitionId")
    public OutcomeDistributionDto getOutcomeDistribution(UUID protocolDefinitionId) {
        ProtocolDefinition pd = protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        List<Object[]> rows = protocolInstanceRepository.countByProtocolDefinitionIdGroupByStatus(protocolDefinitionId);
        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        Map<String, OutcomeDistributionDto.StatusCount> distribution = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String status = row[0].toString().toLowerCase();
            long count = ((Number) row[1]).longValue();
            double pct = total > 0 ? Math.round((double) count / total * 1000.0) / 10.0 : 0;
            distribution.put(status, OutcomeDistributionDto.StatusCount.builder()
                    .count(count).percentage(pct).build());
        }

        return OutcomeDistributionDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .protocolCanonical(pd.getUrl() + "|" + pd.getVersion())
                .totalInstances(total)
                .distribution(distribution)
                .build();
    }

    @Cacheable(value = "analytics", key = "'enrollment-' + #protocolDefinitionId + '-' + #interval")
    public EnrollmentTrendDto getEnrollmentTrends(UUID protocolDefinitionId, String interval,
                                                   OffsetDateTime startDate, OffsetDateTime endDate) {
        protocolDefinitionRepository.findById(protocolDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Protocol definition not found: " + protocolDefinitionId));

        String dbInterval = DateUtil.mapInterval(interval);
        List<Object[]> rows = protocolInstanceRepository.findEnrollmentTrends(
                protocolDefinitionId, dbInterval, startDate, endDate);

        List<EnrollmentTrendDto.TrendPoint> trends = rows.stream().map(row ->
                EnrollmentTrendDto.TrendPoint.builder()
                        .period(DateUtil.extractDate(row[0]))
                        .enrollments(((Number) row[1]).longValue())
                        .build()
        ).collect(Collectors.toList());

        return EnrollmentTrendDto.builder()
                .protocolDefinitionId(protocolDefinitionId)
                .interval(interval != null ? interval : "weekly")
                .trends(trends)
                .build();
    }
}
