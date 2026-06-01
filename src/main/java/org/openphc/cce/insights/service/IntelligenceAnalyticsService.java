package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.entity.DestinationAdaptorMapping;
import org.openphc.cce.insights.domain.entity.ReceiverAdaptor;
import org.openphc.cce.insights.domain.repository.DestinationAdaptorMappingRepository;
import org.openphc.cce.insights.domain.repository.IntelligenceDeliveryRepository;
import org.openphc.cce.insights.domain.repository.ReceiverAdaptorRepository;
import org.openphc.cce.insights.web.dto.IntelligenceSummaryDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntelligenceAnalyticsService {

    private final IntelligenceDeliveryRepository deliveryRepository;
    private final ReceiverAdaptorRepository adaptorRepository;
    private final DestinationAdaptorMappingRepository mappingRepository;

    @Cacheable(value = "metrics", key = "'intelligence-summary-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public IntelligenceSummaryDto getSummary(OffsetDateTime startDate, OffsetDateTime endDate) {
        long total = deliveryRepository.countFiltered(startDate, endDate);
        long delivered = deliveryRepository.countDeliveredFiltered(startDate, endDate);
        long failed = deliveryRepository.countFailedFiltered(startDate, endDate);
        long pending = total - delivered - failed;
        double successRate = total > 0 ? Math.round((double) delivered / total * 1000.0) / 10.0 : 0;
        Double avgLatency = deliveryRepository.avgDeliveryLatencySecondsFiltered(startDate, endDate);

        List<IntelligenceSummaryDto.StatusBreakdown> byStatus = deliveryRepository.countByStatusFiltered(startDate, endDate).stream()
                .map(row -> new IntelligenceSummaryDto.StatusBreakdown((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        List<IntelligenceSummaryDto.StatusBreakdown> byActionType = deliveryRepository.countByActionTypeFiltered(startDate, endDate).stream()
                .map(row -> new IntelligenceSummaryDto.StatusBreakdown((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        List<IntelligenceSummaryDto.StatusBreakdown> bySeverity = deliveryRepository.countBySeverityFiltered(startDate, endDate).stream()
                .map(row -> new IntelligenceSummaryDto.StatusBreakdown((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        List<IntelligenceSummaryDto.DestinationBreakdown> byDestination = deliveryRepository.countByDestinationFiltered(startDate, endDate).stream()
                .map(row -> new IntelligenceSummaryDto.DestinationBreakdown((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        List<IntelligenceSummaryDto.ActiveAdaptor> activeAdaptors = buildActiveAdaptors();

        return IntelligenceSummaryDto.builder()
                .total(total)
                .delivered(delivered)
                .failed(failed)
                .pending(pending)
                .successRate(successRate)
                .avgLatencySeconds(avgLatency)
                .byStatus(byStatus)
                .byActionType(byActionType)
                .bySeverity(bySeverity)
                .byDestination(byDestination)
                .activeAdaptors(activeAdaptors)
                .build();
    }

    private List<IntelligenceSummaryDto.ActiveAdaptor> buildActiveAdaptors() {
        List<ReceiverAdaptor> adaptors = adaptorRepository.findAll();
        List<DestinationAdaptorMapping> mappings = mappingRepository.findAll();

        Map<UUID, List<String>> adaptorDestinations = mappings.stream()
                .collect(Collectors.groupingBy(
                        DestinationAdaptorMapping::getReceiverAdaptorId,
                        Collectors.mapping(DestinationAdaptorMapping::getDestination, Collectors.toList())
                ));

        return adaptors.stream()
                .map(a -> new IntelligenceSummaryDto.ActiveAdaptor(
                        a.getName(),
                        a.getStatus(),
                        adaptorDestinations.getOrDefault(a.getId(), List.of())
                ))
                .toList();
    }
}
