package org.openphc.cce.insights;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.IngestionAnalyticsService;
import org.openphc.cce.insights.web.controller.IngestionAnalyticsController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.*;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionAnalyticsController.class)
@Import(GlobalExceptionHandler.class)
class IngestionAnalyticsControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionAnalyticsService ingestionAnalyticsService;

    @BeforeEach
    void setUp() {
        IngestionFunnelDto baseFunnel = IngestionFunnelDto.builder()
                .totalReceived(100).accepted(90).rejected(5).duplicate(5)
                .acceptanceRate(90.0).rejectionRate(5.0).duplicateRate(5.0)
                .breakdown(List.of(
                        IngestionFunnelDto.StatusBreakdown.builder().status("ACCEPTED").count(90).percentage(90.0).build(),
                        IngestionFunnelDto.StatusBreakdown.builder().status("REJECTED").count(5).percentage(5.0).build(),
                        IngestionFunnelDto.StatusBreakdown.builder().status("DUPLICATE").count(5).percentage(5.0).build()))
                .build();

        when(ingestionAnalyticsService.getIngestionFunnel(isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(baseFunnel);

        when(ingestionAnalyticsService.getIngestionFunnel(isNull(), isNull(), any(), any(), eq("monthly")))
                .thenReturn(IngestionFunnelDto.builder()
                        .totalReceived(100).accepted(90).rejected(5).duplicate(5)
                        .acceptanceRate(90.0).rejectionRate(5.0).duplicateRate(5.0)
                        .breakdown(baseFunnel.getBreakdown())
                        .trends(List.of(IngestionFunnelDto.TrendPoint.builder()
                                .period("2026-03").byStatus(Map.of("ACCEPTED", 90L)).total(100).build()))
                        .build());

        when(ingestionAnalyticsService.getIngestionFunnel(isNull(), eq("ebuzima/kigali-south"), any(), any(), isNull()))
                .thenReturn(baseFunnel);

        when(ingestionAnalyticsService.getRejectionAnalytics(any(), any(), any(), any()))
                .thenReturn(RejectionAnalyticsDto.builder()
                        .totalRejected(5)
                        .byReason(List.of(RejectionAnalyticsDto.ReasonBreakdown.builder()
                                .reason("INVALID_SCHEMA").count(3).percentage(60.0).build()))
                        .bySource(List.of(RejectionAnalyticsDto.SourceRejection.builder()
                                .source("ebuzima-direct").totalEvents(50).rejectedEvents(3).rejectionRate(6.0)
                                .topReasons(List.of()).build()))
                        .build());

        when(ingestionAnalyticsService.getSourceDataQuality(any(), any(), any()))
                .thenReturn(SourceDataQualityDto.builder()
                        .sources(List.of(SourceDataQualityDto.SourceQuality.builder()
                                .source("ebuzima-direct").totalEvents(50).accepted(45)
                                .rejected(3).duplicate(2).acceptanceRate(90.0).build()))
                        .build());

        when(ingestionAnalyticsService.getPipelineLoss(any(), any(), any()))
                .thenReturn(PipelineLossDto.builder()
                        .totalAcceptedByCollector(90).totalInComplianceEventLog(85)
                        .lostEvents(5).lossRate(5.6)
                        .bySource(List.of(PipelineLossDto.SourceLoss.builder()
                                .source("ebuzima-direct").lostEvents(3).build()))
                        .build());
    }

    @Test
    void funnel_returnsStatusBreakdown() throws Exception {
        mockMvc.perform(get("/v1/insights/ingestion/funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReceived").isNumber())
                .andExpect(jsonPath("$.data.accepted").isNumber())
                .andExpect(jsonPath("$.data.rejected").isNumber())
                .andExpect(jsonPath("$.data.duplicate").isNumber())
                .andExpect(jsonPath("$.data.acceptanceRate").isNumber())
                .andExpect(jsonPath("$.data.breakdown").isArray());
    }

    @Test
    void funnel_withInterval_returnsTrends() throws Exception {
        mockMvc.perform(get("/v1/insights/ingestion/funnel")
                        .param("interval", "monthly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trends").isArray());
    }

    @Test
    void funnel_filteredBySource() throws Exception {
        mockMvc.perform(get("/v1/insights/ingestion/funnel")
                        .param("source", "ebuzima/kigali-south"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReceived").isNumber());
    }

    @Test
    void rejections_returnsReasonBreakdown() throws Exception {
        mockMvc.perform(get("/v1/insights/ingestion/rejections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRejected").isNumber())
                .andExpect(jsonPath("$.data.byReason").isArray())
                .andExpect(jsonPath("$.data.byReason[0].reason").isString())
                .andExpect(jsonPath("$.data.byReason[0].count").isNumber());
    }

    @Test
    void rejections_includesSourceDetail() throws Exception {
        mockMvc.perform(get("/v1/insights/ingestion/rejections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bySource").isArray());
    }

    @Test
    void sourceQuality_returnsPerSourceMetrics() throws Exception {
        mockMvc.perform(get("/v1/insights/ingestion/source-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources").isArray())
                .andExpect(jsonPath("$.data.sources[0].source").isString())
                .andExpect(jsonPath("$.data.sources[0].totalEvents").isNumber())
                .andExpect(jsonPath("$.data.sources[0].accepted").isNumber())
                .andExpect(jsonPath("$.data.sources[0].acceptanceRate").isNumber());
    }

    @Test
    void pipelineLoss_detectsLostEvents() throws Exception {
        mockMvc.perform(get("/v1/insights/ingestion/pipeline-loss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAcceptedByCollector").isNumber())
                .andExpect(jsonPath("$.data.totalInComplianceEventLog").isNumber())
                .andExpect(jsonPath("$.data.lostEvents").isNumber())
                .andExpect(jsonPath("$.data.lossRate").isNumber());
    }

    @Test
    void pipelineLoss_returnsLossBySource() throws Exception {
        mockMvc.perform(get("/v1/insights/ingestion/pipeline-loss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bySource").isArray());
    }
}
