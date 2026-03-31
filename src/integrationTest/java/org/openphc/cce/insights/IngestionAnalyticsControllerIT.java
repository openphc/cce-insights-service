package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IngestionAnalyticsControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // --- Funnel ---

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

    // --- Rejections ---

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

    // --- Source Quality ---

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

    // --- Pipeline Loss ---

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
