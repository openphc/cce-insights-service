package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FacilityRankingControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getFacilityRanking_returnsRankedList() throws Exception {
        mockMvc.perform(get("/v1/insights/facilities/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
