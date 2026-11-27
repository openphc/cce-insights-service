package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.service.DashboardService;
import org.openphc.cce.insights.service.HandoffMetricsService;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.controller.DashboardController;
import org.openphc.cce.insights.web.dto.HandoffMetricDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(GlobalExceptionHandler.class)
class DashboardControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private HandoffMetricsService handoffMetricsService;

    @Test
    void getHandoffs_returnsOneEntryPerHandoff() throws Exception {
        when(handoffMetricsService.getHandoffMetrics(any(), any())).thenReturn(List.of(
                HandoffMetricDto.builder().key("pci-referral-acknowledged").actionId("ncd-pci-acknowledged")
                        .total(4).onTime(3).completedLate(1).deviations(1).deviationRate(25.0).medianMinutes(0.8)
                        .build()));

        mockMvc.perform(get("/v1/insights/dashboard/handoffs")
                        .param("startDate", "2026-11-01T00:00:00Z")
                        .param("endDate", "2026-11-30T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("pci-referral-acknowledged"))
                .andExpect(jsonPath("$.data[0].deviations").value(1))
                .andExpect(jsonPath("$.data[0].deviationRate").value(25.0))
                .andExpect(jsonPath("$.data[0].medianMinutes").value(0.8));
    }
}
