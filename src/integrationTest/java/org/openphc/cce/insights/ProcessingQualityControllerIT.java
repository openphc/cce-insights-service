package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProcessingQualityControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getProcessingQuality_returnsQualityMetrics() throws Exception {
        mockMvc.perform(get("/v1/insights/events/processing-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvents").isNumber())
                .andExpect(jsonPath("$.data.overall").isMap());
    }
}
