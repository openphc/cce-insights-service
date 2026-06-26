package org.openphc.cce.insights;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.ProtocolAnalyticsService;
import org.openphc.cce.insights.web.controller.ProtocolAnalyticsController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.*;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProtocolAnalyticsController.class)
@Import(GlobalExceptionHandler.class)
class ProtocolAnalyticsControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProtocolAnalyticsService protocolAnalyticsService;

    private static final UUID PROTOCOL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @BeforeEach
    void setUp() {
        when(protocolAnalyticsService.getStepAnalytics(eq(PROTOCOL_ID), any(), any(), any()))
                .thenReturn(StepAnalyticsDto.builder()
                        .protocolDefinitionId(PROTOCOL_ID)
                        .protocolCanonical("http://example.org/anc|1.0")
                        .steps(List.of(StepAnalyticsDto.StepMetric.builder()
                                .actionId("visit-1").totalInstances(3).completedCount(2)
                                .completionRate(0.67)
                                .timelinessDistribution(StepAnalyticsDto.TimelinessDistribution.builder()
                                        .early(0).onTime(1).late(1).build())
                                .overdueCount(0).missedCount(0).skippedCount(0).pendingCount(1)
                                .build()))
                        .build());

        when(protocolAnalyticsService.getCompletionFunnel(eq(PROTOCOL_ID), any(), any(), any()))
                .thenReturn(CompletionFunnelDto.builder()
                        .protocolDefinitionId(PROTOCOL_ID)
                        .protocolCanonical("http://example.org/anc|1.0")
                        .totalEnrollments(3)
                        .funnel(List.of(CompletionFunnelDto.FunnelStep.builder()
                                .actionId("visit-1").stepOrder(1).reachedCount(3)
                                .completedCount(2).completionRate(0.67).dropOffRate(0.33).build()))
                        .build());

        when(protocolAnalyticsService.getOutcomeDistribution(eq(PROTOCOL_ID), any(), any(), any()))
                .thenReturn(OutcomeDistributionDto.builder()
                        .protocolDefinitionId(PROTOCOL_ID)
                        .protocolCanonical("http://example.org/anc|1.0")
                        .totalInstances(3)
                        .distribution(Map.of("active", OutcomeDistributionDto.StatusCount.builder()
                                .count(3).percentage(100.0).build()))
                        .build());

        when(protocolAnalyticsService.getEnrollmentTrends(eq(PROTOCOL_ID), eq("monthly"), any(), any(), any()))
                .thenReturn(EnrollmentTrendDto.builder()
                        .protocolDefinitionId(PROTOCOL_ID)
                        .interval("monthly")
                        .trends(List.of(EnrollmentTrendDto.TrendPoint.builder()
                                .period("2026-03").enrollments(3).build()))
                        .build());

        when(protocolAnalyticsService.getStepAnalytics(eq(UNKNOWN_ID), any(), any(), any()))
                .thenThrow(new EntityNotFoundException("Protocol definition not found: " + UNKNOWN_ID));
    }

    @Test
    void getStepAnalytics_returnsPerStepMetrics() throws Exception {
        mockMvc.perform(get("/v1/insights/protocols/550e8400-e29b-41d4-a716-446655440000/step-analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protocolDefinitionId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.data.steps").isArray());
    }

    @Test
    void getCompletionFunnel_returnsFunnelData() throws Exception {
        mockMvc.perform(get("/v1/insights/protocols/550e8400-e29b-41d4-a716-446655440000/completion-funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEnrollments").value(3))
                .andExpect(jsonPath("$.data.funnel").isArray());
    }

    @Test
    void getOutcomeDistribution_returnsDistribution() throws Exception {
        mockMvc.perform(get("/v1/insights/protocols/550e8400-e29b-41d4-a716-446655440000/outcome-distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInstances").value(3))
                .andExpect(jsonPath("$.data.distribution").isMap());
    }

    @Test
    void getEnrollmentTrends_returnsTrends() throws Exception {
        mockMvc.perform(get("/v1/insights/protocols/550e8400-e29b-41d4-a716-446655440000/enrollment-trends")
                        .param("interval", "monthly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("monthly"))
                .andExpect(jsonPath("$.data.trends").isArray());
    }

    @Test
    void getStepAnalytics_notFound() throws Exception {
        mockMvc.perform(get("/v1/insights/protocols/00000000-0000-0000-0000-000000000000/step-analytics"))
                .andExpect(status().isNotFound());
    }
}
