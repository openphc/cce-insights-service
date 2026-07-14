package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.EventKpiDto;
import org.openphc.cce.insights.web.dto.EventVolumeSummaryDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventVolumeService} — the Events-page reconciliation math over the
 * event_time processing MV (mv_daily_event_kpis). These lock the invariants the UI relies on:
 * every card is a share of the same total, pipeline loss is a non-negative pass-through, and
 * a zero-event period never divides by zero. Repositories are mocked so no ClickHouse is needed.
 */
class EventVolumeServiceTest {

    private final ComplianceEventLogRepository complianceRepo = mock(ComplianceEventLogRepository.class);
    private final InboundEventRepository inboundRepo = mock(InboundEventRepository.class);
    private final DailyKpiRepository dailyKpiRepo = mock(DailyKpiRepository.class);
    private final EventVolumeService service = new EventVolumeService(complianceRepo, inboundRepo, dailyKpiRepo);

    /** eventProcessingKpis row shape: [total, matched, zeroMatch, duplicate, pipelineLoss]. */
    private static Object[] proc(long total, long matched, long zero, long dup, long loss) {
        return new Object[]{total, matched, zero, dup, loss};
    }

    @Test
    void getSummary_reconcilesEveryCardAgainstTotal() {
        // The real reconciliation case from the migration: 108 = 44 matched + 60 zero + 0 dup + 4 loss.
        when(inboundRepo.eventProcessingKpis(any(), any(), any())).thenReturn(proc(108, 44, 60, 0, 4));
        when(inboundRepo.eventVolumeByFacilityAndType(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(inboundRepo.eventVolumeBySource(any(), any(), any())).thenReturn(List.of());

        EventVolumeSummaryDto dto = service.getSummary(null, null, null, null);

        assertThat(dto.getTotalEvents()).isEqualTo(108);
        assertThat(dto.getPipelineLossCount()).isEqualTo(4);
        // Percentages are each count / total, rounded to one decimal.
        assertThat(dto.getProcessingStatusBreakdown().get("matched").getCount()).isEqualTo(44);
        assertThat(dto.getProcessingStatusBreakdown().get("matched").getPercentage()).isEqualTo(40.7);
        assertThat(dto.getProcessingStatusBreakdown().get("zeroMatch").getPercentage()).isEqualTo(55.6);
        assertThat(dto.getProcessingStatusBreakdown().get("duplicate").getPercentage()).isEqualTo(0.0);

        // Structural invariant: matched + zeroMatch + duplicate + pipelineLoss == total.
        long parts = dto.getProcessingStatusBreakdown().get("matched").getCount()
                + dto.getProcessingStatusBreakdown().get("zeroMatch").getCount()
                + dto.getProcessingStatusBreakdown().get("duplicate").getCount()
                + dto.getPipelineLossCount();
        assertThat(parts).isEqualTo(dto.getTotalEvents());
    }

    @Test
    void getSummary_pipelineLossIsNeverNegativeAndZeroTotalDoesNotDivideByZero() {
        // A period with no clinical events must yield zeroed cards, not NaN/Infinity or an exception.
        when(inboundRepo.eventProcessingKpis(any(), any(), any())).thenReturn(proc(0, 0, 0, 0, 0));
        when(inboundRepo.eventVolumeByFacilityAndType(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(inboundRepo.eventVolumeBySource(any(), any(), any())).thenReturn(List.of());

        EventVolumeSummaryDto dto = service.getSummary(null, null, null, null);

        assertThat(dto.getTotalEvents()).isZero();
        assertThat(dto.getPipelineLossCount()).isGreaterThanOrEqualTo(0);
        assertThat(dto.getProcessingStatusBreakdown().get("matched").getPercentage()).isEqualTo(0.0);
        assertThat(dto.getProcessingStatusBreakdown().get("zeroMatch").getPercentage()).isEqualTo(0.0);
        assertThat(dto.getProcessingStatusBreakdown().get("duplicate").getPercentage()).isEqualTo(0.0);
    }

    @Test
    void getSummary_aggregatesFacilitiesAndOrdersByVolumeDescending() {
        // Rows: [facility_id, resource_type, count]. F-A splits across two resource types (30+5),
        // so it must out-rank F-B (20) after aggregation.
        when(inboundRepo.eventProcessingKpis(any(), any(), any())).thenReturn(proc(55, 55, 0, 0, 0));
        when(inboundRepo.eventVolumeByFacilityAndType(any(), any(), any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"F-A", "Encounter", 30L},
                new Object[]{"F-B", "Observation", 20L},
                new Object[]{"F-A", "Condition", 5L}));
        when(inboundRepo.eventVolumeBySource(any(), any(), any())).thenReturn(List.of());

        EventVolumeSummaryDto dto = service.getSummary(null, null, null, null);

        assertThat(dto.getByFacility()).hasSize(2);
        assertThat(dto.getByFacility().get(0).getFacilityId()).isEqualTo("F-A");
        assertThat(dto.getByFacility().get(0).getCount()).isEqualTo(35);
        assertThat(dto.getByFacility().get(1).getFacilityId()).isEqualTo("F-B");
        assertThat(dto.getByFacility().get(1).getCount()).isEqualTo(20);
    }

    @Test
    void getEventKpis_computesRatesAndPassesCountsThrough() {
        when(inboundRepo.eventProcessingKpis(null, null, null)).thenReturn(proc(100, 80, 15, 5, 2));

        EventKpiDto dto = service.getEventKpis();

        assertThat(dto.getTotalEvents()).isEqualTo(100);
        assertThat(dto.getMatchedCount()).isEqualTo(80);
        assertThat(dto.getZeroMatchCount()).isEqualTo(15);
        assertThat(dto.getDuplicateCount()).isEqualTo(5);
        assertThat(dto.getMatchedRatePct()).isEqualTo(80.0);
        assertThat(dto.getZeroMatchRatePct()).isEqualTo(15.0);
        assertThat(dto.getPipelineLossCount()).isEqualTo(2);
    }

    @Test
    void getEventKpis_zeroTotalYieldsZeroRates() {
        when(inboundRepo.eventProcessingKpis(null, null, null)).thenReturn(proc(0, 0, 0, 0, 0));

        EventKpiDto dto = service.getEventKpis();

        assertThat(dto.getMatchedRatePct()).isEqualTo(0.0);
        assertThat(dto.getZeroMatchRatePct()).isEqualTo(0.0);
    }
}
