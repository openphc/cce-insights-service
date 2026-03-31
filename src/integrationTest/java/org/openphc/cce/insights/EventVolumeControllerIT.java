package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventVolumeControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getSummary_returnsTotalEvents() throws Exception {
        mockMvc.perform(get("/v1/insights/events/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvents").isNumber());
    }

    @Test
    void getTrends_returnsTrendData() throws Exception {
        mockMvc.perform(get("/v1/insights/events/trends").param("interval", "monthly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interval").value("monthly"))
                .andExpect(jsonPath("$.data.trends").isArray());
    }

    @Test
    void getByResourceType_returnsGroupedCounts() throws Exception {
        mockMvc.perform(get("/v1/insights/events/by-resource-type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].resourceType").isString());
    }

    @Test
    void getByFacility_returnsGroupedCounts() throws Exception {
        mockMvc.perform(get("/v1/insights/events/by-facility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getByPractitioner_returnsGroupedCounts() throws Exception {
        mockMvc.perform(get("/v1/insights/events/by-practitioner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getBySource_returnsGroupedCounts() throws Exception {
        mockMvc.perform(get("/v1/insights/events/by-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void sourceComparison_returnsOverlapAndUnique() throws Exception {
        mockMvc.perform(get("/v1/insights/events/source-comparison")
                        .param("sourceA", "ebuzima/kigali-south")
                        .param("sourceB", "rhie-mediator")
                        .param("windowSeconds", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceA").value("ebuzima/kigali-south"))
                .andExpect(jsonPath("$.data.sourceB").value("rhie-mediator"))
                .andExpect(jsonPath("$.data.matchWindowSeconds").value(300))
                .andExpect(jsonPath("$.data.sourceASummary.source").value("ebuzima/kigali-south"))
                .andExpect(jsonPath("$.data.sourceASummary.totalEvents").isNumber())
                .andExpect(jsonPath("$.data.sourceASummary.uniqueEvents").isNumber())
                .andExpect(jsonPath("$.data.sourceBSummary.source").value("rhie-mediator"))
                .andExpect(jsonPath("$.data.overlap.totalOverlappingEvents").isNumber())
                .andExpect(jsonPath("$.data.overlap.byResourceType").isArray());
    }

    @Test
    void sourceComparison_requiresBothSources() throws Exception {
        mockMvc.perform(get("/v1/insights/events/source-comparison")
                        .param("sourceA", "ebuzima/kigali-south"))
                .andExpect(status().isBadRequest());
    }
}
