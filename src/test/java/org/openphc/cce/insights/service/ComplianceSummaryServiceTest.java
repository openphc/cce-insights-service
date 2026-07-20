package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.repository.ComplianceEventLogRepository;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.domain.repository.ProtocolDefinitionRepository;
import org.openphc.cce.insights.domain.repository.ProtocolInstanceRepository;
import org.openphc.cce.insights.domain.repository.StepInstanceRepository;
import org.openphc.cce.insights.web.dto.ComplianceSummaryDto;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RI-36 — Compliance Overview "eventTime" (Clinical Event Date) mode. In this mode the all-protocols
 * patient block is the matched-event cohort (by clinical event_time), the SAME queries as
 * {@link DashboardService#getComplianceSummary}, so the Compliance Overview reconciles with the
 * Dashboard "Service Compliance" card. Enrollment mode is unchanged. Repositories mocked.
 */
class ComplianceSummaryServiceTest {

    private final ProtocolDefinitionRepository protocolDef = mock(ProtocolDefinitionRepository.class);
    private final ProtocolInstanceRepository protocolInstance = mock(ProtocolInstanceRepository.class);
    private final StepInstanceRepository stepInstance = mock(StepInstanceRepository.class);
    private final DeviationRepository deviation = mock(DeviationRepository.class);
    private final ComplianceEventLogRepository complianceEventLog = mock(ComplianceEventLogRepository.class);
    private final DailyKpiRepository dailyKpi = mock(DailyKpiRepository.class);
    private final InboundEventRepository inbound = mock(InboundEventRepository.class);

    private final ComplianceSummaryService service = new ComplianceSummaryService(
            protocolDef, protocolInstance, stepInstance, deviation, complianceEventLog, dailyKpi, inbound);

    /** step-metric aggregate row: [completed, overdue, missed, due, pending, early, onTime, late, totalSteps, ...]. */
    private static Object[] stepRow() {
        return new Object[]{40L, 0L, 0L, 0L, 0L, 0L, 40L, 0L, 60L, 50L, 44L};
    }

    @Test
    void getAllProtocolsComplianceSummary_eventTimeMode_isMatchedEventCohortMinusDeviators() {
        OffsetDateTime start = OffsetDateTime.parse("2026-04-01T00:00:00Z");
        OffsetDateTime end   = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        when(inbound.countDistinctPatientsWithMatchedEvents(any(), any(), any())).thenReturn(8L);
        when(deviation.countDistinctNonCompliantAmongMatched(any(), any(), any())).thenReturn(2L);
        when(dailyKpi.getComplianceKpisAll(any())).thenReturn(stepRow());
        when(deviation.countByTypeFiltered(any(), any(), any(), any()))
                .thenReturn(List.of(new Object[]{"OVERDUE", 1L}, new Object[]{"MISSED", 1L}));

        ComplianceSummaryDto dto = service.getAllProtocolsComplianceSummary(null, start, end, "eventTime");

        assertThat(dto.getTotalEnrollments()).isEqualTo(8);        // tracked = matched-event cohort
        assertThat(dto.getCompliantPatients()).isEqualTo(6);       // 8 - 2
        assertThat(dto.getComplianceRate()).isEqualTo(75.0);       // 6 / 8
        assertThat(dto.getStepMetrics().getTotalSteps()).isEqualTo(60);   // step snapshot preserved
    }

    @Test
    void getAllProtocolsComplianceSummary_eventTimeMode_zeroTrackedIsEmpty() {
        when(inbound.countDistinctPatientsWithMatchedEvents(any(), any(), any())).thenReturn(0L);

        ComplianceSummaryDto dto = service.getAllProtocolsComplianceSummary(null, null, null, "eventTime");

        assertThat(dto.getTotalEnrollments()).isZero();
        assertThat(dto.getCompliantPatients()).isZero();
        assertThat(dto.getComplianceRate()).isEqualTo(0.0);
    }
}
