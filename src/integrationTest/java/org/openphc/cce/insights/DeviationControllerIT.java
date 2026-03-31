package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeviationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getDeviationTrends_returnsTrends() throws Exception {
        mockMvc.perform(get("/v1/deviations/trends")
                        .param("interval", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("weekly"))
                .andExpect(jsonPath("$.data.trends").isArray());
    }

    @Test
    void getIntelligenceSummary_returnsSummary() throws Exception {
        mockMvc.perform(get("/v1/intelligence/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDeviations").isNumber())
                .andExpect(jsonPath("$.data.byType").isMap())
                .andExpect(jsonPath("$.data.recentActivity").isMap());
    }

    @Test
    void getDeviationsByAction_returnsResults() throws Exception {
        mockMvc.perform(get("/v1/deviations/by-action"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getResolutionRate_returnsResult() throws Exception {
        mockMvc.perform(get("/v1/deviations/resolution-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOverdueDeviations").isNumber());
    }
}
