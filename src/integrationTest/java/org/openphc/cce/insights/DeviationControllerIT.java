package org.openphc.cce.insights;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.DeviationAnalyticsService;
import org.openphc.cce.insights.web.controller.DeviationController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.*;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviationController.class)
@Import(GlobalExceptionHandler.class)
class DeviationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviationAnalyticsService deviationAnalyticsService;

    @BeforeEach
    void setUp() {
        when(deviationAnalyticsService.getDeviationTrends(eq("weekly"), any(), any(), any()))
                .thenReturn(DeviationTrendDto.builder()
                        .interval("weekly")
                        .trends(List.of(DeviationTrendDto.TrendPoint.builder()
                                .period("2026-W12").overdue(2).missed(1).orderViolation(0).total(3).build()))
                        .build());

        when(deviationAnalyticsService.getIntelligenceSummary())
                .thenReturn(DeviationIntelligenceSummaryDto.builder()
                        .totalDeviations(5)
                        .byType(Map.of("overdue", 3L, "missed", 2L, "orderViolation", 0L))
                        .bySeverity(Map.of("warning", 3L, "critical", 2L))
                        .recentActivity(DeviationIntelligenceSummaryDto.RecentActivity.builder()
                                .last24Hours(1).last7Days(3).last30Days(5).build())
                        .build());

        when(deviationAnalyticsService.getDeviationsByAction(any(), any(), any()))
                .thenReturn(List.of(DeviationByActionDto.builder()
                        .actionId("visit-1").totalDeviations(2).build()));

        when(deviationAnalyticsService.getResolutionRate(any(), any(), any()))
                .thenReturn(DeviationResolutionDto.builder()
                        .totalOverdueDeviations(3)
                        .resolved(DeviationResolutionDto.Resolution.builder().count(1).percentage(33.3).build())
                        .escalatedToMissed(DeviationResolutionDto.Escalation.builder().count(1).percentage(33.3).build())
                        .byProtocol(List.of())
                        .build());
    }

    @Test
    void getDeviationTrends_returnsTrends() throws Exception {
        mockMvc.perform(get("/v1/insights/deviations/trends")
                        .param("interval", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("weekly"))
                .andExpect(jsonPath("$.data.trends").isArray());
    }

    @Test
    void getIntelligenceSummary_returnsSummary() throws Exception {
        mockMvc.perform(get("/v1/insights/deviations/intelligence-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDeviations").isNumber())
                .andExpect(jsonPath("$.data.byType").isMap())
                .andExpect(jsonPath("$.data.recentActivity").isMap());
    }

    @Test
    void getDeviationsByAction_returnsResults() throws Exception {
        mockMvc.perform(get("/v1/insights/deviations/by-action"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getResolutionRate_returnsResult() throws Exception {
        mockMvc.perform(get("/v1/insights/deviations/resolution-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOverdueDeviations").isNumber());
    }
}
