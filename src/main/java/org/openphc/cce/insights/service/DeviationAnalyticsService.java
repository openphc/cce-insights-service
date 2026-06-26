package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviationAnalyticsService {

    private final DeviationRepository deviationRepository;

    public List<DeviationDto> getDeviations(String deviationType, String facilityId,
                                             UUID protocolDefinitionId,
                                             OffsetDateTime startDate, OffsetDateTime endDate,
                                             int limit) {
        List<Object[]> rows = deviationRepository.findFilteredDeviations(
                deviationType, facilityId, protocolDefinitionId, startDate, endDate, limit);
        return rows.stream().map(row -> DeviationDto.builder()
                .deviationId(uuidOf(row[0]))
                .patientId((String) row[1])
                .protocolInstanceId(uuidOf(row[2]))
                .protocolCanonical((String) row[3])
                .stepInstanceId(uuidOf(row[4]))
                .actionId((String) row[5])
                .deviationType((String) row[6])
                .detectedAt(DateUtil.toOffsetDateTime(row[7]))
                .facilityId((String) row[8])
                .build()).collect(Collectors.toList());
    }

    @Cacheable(value = "analytics", key = "'deviation-kpis-' + (#protocolDefinitionId ?: 'all') + '-' + (#facilityId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public DeviationKpiDto getDeviationKpis(UUID protocolDefinitionId, String facilityId,
                                             OffsetDateTime startDate, OffsetDateTime endDate) {
        // Counts distinct deviation rows in the deviations table, optionally scoped to
        // protocol, facility (via mv_patient_facility_latest) and detection date range.
        // Replaces summing mv_daily_deviation_kpis snapshot rows, which inflated totals
        // by counting the same active deviation on every day it appeared in the snapshot.
        List<Object[]> rows = deviationRepository.countByTypeFiltered(
                protocolDefinitionId, facilityId, startDate, endDate);
        long total = 0, overdue = 0, missed = 0, orderViolation = 0;
        for (Object[] row : rows) {
            long c = ((Number) row[1]).longValue();
            total += c;
            switch ((String) row[0]) {
                case "OVERDUE" -> overdue = c;
                case "MISSED" -> missed = c;
                case "ORDER_VIOLATION" -> orderViolation = c;
                default -> {}
            }
        }
        return DeviationKpiDto.builder()
                .totalDeviations(total)
                .overdueCount(overdue)
                .missedCount(missed)
                .orderViolationCount(orderViolation)
                .build();
    }

    @Cacheable(value = "analytics", key = "'dev-trends-' + #interval + '-' + (#protocolDefinitionId ?: 'all') + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public DeviationTrendDto getDeviationTrends(String interval, OffsetDateTime startDate,
                                                 OffsetDateTime endDate, String facilityId,
                                                 UUID protocolDefinitionId) {
        String dbInterval = DateUtil.mapInterval(interval);
        List<Object[]> rows = deviationRepository.findDeviationTrends(dbInterval, startDate, endDate, facilityId, protocolDefinitionId);

        Map<String, DeviationTrendDto.TrendPoint.TrendPointBuilder> pointMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String period = DateUtil.extractDate(row[0]);
            String type = (String) row[1];
            long count = ((Number) row[2]).longValue();

            pointMap.computeIfAbsent(period, p -> DeviationTrendDto.TrendPoint.builder()
                    .period(p).overdue(0).missed(0).orderViolation(0).total(0));
            DeviationTrendDto.TrendPoint.TrendPointBuilder builder = pointMap.get(period);
            DeviationTrendDto.TrendPoint partial = builder.build();
            if ("OVERDUE".equals(type)) {
                pointMap.put(period, builder.overdue(count).total(partial.getTotal() + count));
            } else if ("ORDER_VIOLATION".equals(type)) {
                pointMap.put(period, builder.orderViolation(count).total(partial.getTotal() + count));
            } else {
                pointMap.put(period, builder.missed(count).total(partial.getTotal() + count));
            }
        }

        List<DeviationTrendDto.TrendPoint> trends = pointMap.values().stream()
                .map(DeviationTrendDto.TrendPoint.TrendPointBuilder::build)
                .collect(Collectors.toList());

        return DeviationTrendDto.builder().interval(interval).trends(trends).build();
    }

    @Cacheable(value = "analytics", key = "'intelligence-summary-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all') + '-' + (#facilityId ?: 'all')")
    public DeviationIntelligenceSummaryDto getIntelligenceSummary(OffsetDateTime startDate,
                                                                   OffsetDateTime endDate,
                                                                   String facilityId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Object[]> allDeviations = deviationRepository.countByTypeInRange(startDate, endDate, facilityId);
        // Recent-activity windows respect the selected facility so the header tile and the
        // recent-activity counts stay consistent when a facility is chosen.
        List<Object[]> last24h = deviationRepository.countByTypeSince(now.minusHours(24), facilityId);
        List<Object[]> last7d = deviationRepository.countByTypeSince(now.minusDays(7), facilityId);
        List<Object[]> last30d = deviationRepository.countByTypeSince(now.minusDays(30), facilityId);

        long total = 0, overdueCount = 0, missedCount = 0, orderViolationCount = 0;
        for (Object[] row : allDeviations) {
            long c = ((Number) row[1]).longValue();
            String type = (String) row[0];
            total += c;
            switch (type) {
                case "OVERDUE" -> overdueCount = c;
                case "MISSED" -> missedCount = c;
                case "ORDER_VIOLATION" -> orderViolationCount = c;
            }
        }

        return DeviationIntelligenceSummaryDto.builder()
                .totalDeviations(total)
                .byType(Map.of("overdue", overdueCount, "missed", missedCount, "orderViolation", orderViolationCount))
                .bySeverity(Map.of("warning", overdueCount, "critical", missedCount + orderViolationCount))
                .recentActivity(DeviationIntelligenceSummaryDto.RecentActivity.builder()
                        .last24Hours(sumCounts(last24h))
                        .last7Days(sumCounts(last7d))
                        .last30Days(sumCounts(last30d))
                        .build())
                .build();
    }

    @Cacheable(value = "analytics", key = "'dev-action-' + #protocolDefId + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public List<DeviationByActionDto> getDeviationsByAction(UUID protocolDefId,
                                                             OffsetDateTime startDate,
                                                             OffsetDateTime endDate) {
        List<Object[]> rows = deviationRepository.findDeviationsByAction(protocolDefId, startDate, endDate);
        return rows.stream().map(row -> DeviationByActionDto.builder()
                .actionId((String) row[0])
                .protocolDefinitionId(uuidOf(row[1]))
                .protocolCanonical((String) row[2])
                .totalDeviations(((Number) row[3]).longValue())
                .overdueCount(((Number) row[4]).longValue())
                .missedCount(((Number) row[5]).longValue())
                .orderViolationCount(((Number) row[6]).longValue())
                .affectedPatients(((Number) row[7]).longValue())
                .build()).collect(Collectors.toList());
    }

    @Cacheable(value = "analytics", key = "'dev-resolution-' + #protocolDefId + '-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public DeviationResolutionDto getResolutionRate(UUID protocolDefId,
                                                     OffsetDateTime startDate,
                                                     OffsetDateTime endDate) {
        List<Object[]> rows = deviationRepository.findResolutionRate(protocolDefId, startDate, endDate);
        if (rows.isEmpty()) {
            return DeviationResolutionDto.builder()
                    .totalOverdueDeviations(0)
                    .resolved(DeviationResolutionDto.Resolution.builder().count(0).percentage(0).build())
                    .escalatedToMissed(DeviationResolutionDto.Escalation.builder().count(0).percentage(0).build())
                    .byProtocol(List.of())
                    .build();
        }

        Object[] row = rows.get(0);
        long resolved = ((Number) row[0]).longValue();
        long escalated = ((Number) row[1]).longValue();
        long total = ((Number) row[2]).longValue();
        Double avgDays = row[3] != null ? ((Number) row[3]).doubleValue() : null;

        double resolvedPct = total > 0 ? Math.round((double) resolved / total * 1000.0) / 10.0 : 0;
        double escalatedPct = total > 0 ? Math.round((double) escalated / total * 1000.0) / 10.0 : 0;

        return DeviationResolutionDto.builder()
                .totalOverdueDeviations(total)
                .resolved(DeviationResolutionDto.Resolution.builder()
                        .count(resolved).percentage(resolvedPct).avgDaysToResolve(avgDays).build())
                .escalatedToMissed(DeviationResolutionDto.Escalation.builder()
                        .count(escalated).percentage(escalatedPct).build())
                .byProtocol(List.of())
                .build();
    }

    private long sumCounts(List<Object[]> rows) {
        return rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
    }

    private static UUID uuidOf(Object value) {
        if (value == null) return null;
        if (value instanceof UUID u) return u;
        String s = value.toString().trim();
        return s.isEmpty() ? null : UUID.fromString(s);
    }
}
