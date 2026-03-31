package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProtocolAnalyticsControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
