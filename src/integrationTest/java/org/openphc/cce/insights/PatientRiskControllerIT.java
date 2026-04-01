package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PatientRiskControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAtRiskHotspots_returnsHotspots() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/at-risk-hotspots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getRepeatDeviations_returnsPatientsAboveThreshold() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/repeat-deviations")
                        .param("minDeviations", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
