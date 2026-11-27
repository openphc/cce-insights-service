package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.config.HandoffProperties;
import org.openphc.cce.insights.config.HandoffProperties.Handoff;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.HandoffMetricDto;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hand-off metrics: per configured hand-off, in order, from the per-step counts. The repository is mocked. */
class HandoffMetricsServiceTest {

    private final StepInstanceRepository repository = mock(StepInstanceRepository.class);
    private final HandoffProperties properties = new HandoffProperties(List.of(
            new Handoff("referral-consultation", "consultation", "referral"),
            new Handoff("pci-acknowledged", "acknowledged", null)));
    private final HandoffMetricsService service = new HandoffMetricsService(properties, repository);

    @Test
    void countsOnTimeLateAndOutstandingHandoffs() {
        // [action_id, total, completed, on_time, completed_late, overdue_open, pending, median_seconds]
        when(repository.findHandoffMetrics(any(), any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"consultation", 10L, 8L, 6L, 2L, 1L, 1L, 90.0}));

        HandoffMetricDto consultation = service.getHandoffMetrics(null, null).get(0);

        assertThat(consultation.getKey()).isEqualTo("referral-consultation");
        assertThat(consultation.getActionId()).isEqualTo("consultation");
        assertThat(consultation.getTotal()).isEqualTo(10);
        assertThat(consultation.getOnTime()).isEqualTo(6);
        assertThat(consultation.getCompletedLate()).isEqualTo(2);
        assertThat(consultation.getOverdueOpen()).isEqualTo(1);
        assertThat(consultation.getPending()).isEqualTo(1);
        // late + outstanding past the deadline
        assertThat(consultation.getDeviations()).isEqualTo(3);
        // 3 of the 9 judged (the pending one is not judged yet)
        assertThat(consultation.getDeviationRate()).isEqualTo(33.3);
        assertThat(consultation.getMedianMinutes()).isEqualTo(1.5);
    }

    @Test
    void keepsTheConfiguredOrderAndShowsZerosForAHandoffWithNothingDue() {
        when(repository.findHandoffMetrics(any(), any(), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"acknowledged", 2L, 2L, 0L, 2L, 0L, 0L, Double.NaN}));

        List<HandoffMetricDto> metrics = service.getHandoffMetrics(null, null);

        assertThat(metrics).extracting(HandoffMetricDto::getKey)
                .containsExactly("referral-consultation", "pci-acknowledged");
        assertThat(metrics.get(0).getTotal()).isZero();
        assertThat(metrics.get(0).getDeviationRate()).isZero();
        assertThat(metrics.get(0).getMedianMinutes()).isNull();
        assertThat(metrics.get(1).getDeviationRate()).isEqualTo(100.0);
        // ClickHouse's median of nothing is NaN: no time shown
        assertThat(metrics.get(1).getMedianMinutes()).isNull();
    }

    @Test
    void asksForEachHandoffsStepAndTheStepItStartsFrom() {
        when(repository.findHandoffMetrics(any(), any(), any(), any())).thenReturn(List.of());

        service.getHandoffMetrics(null, null);

        verify(repository).findHandoffMetrics(eq(List.of("consultation", "acknowledged")),
                eq(Arrays.asList("referral", null)), any(), any());
    }

    @Test
    void noHandoffsConfigured_returnsNothing() {
        HandoffMetricsService none = new HandoffMetricsService(new HandoffProperties(null), repository);
        assertThat(none.getHandoffMetrics(null, null)).isEmpty();
    }
}
