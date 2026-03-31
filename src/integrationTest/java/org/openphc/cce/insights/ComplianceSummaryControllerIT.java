package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComplianceSummaryControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getProtocolComplianceSummary_returnsAggregatedMetrics() throws Exception {
        mockMvc.perform(get("/v1/protocols/550e8400-e29b-41d4-a716-446655440000/compliance-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protocolDefinitionId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.data.totalEnrollments").value(3))
                .andExpect(jsonPath("$.data.complianceRate").isNumber())
                .andExpect(jsonPath("$.data.stepMetrics.totalSteps").value(9))
                .andExpect(jsonPath("$.data.deviationCount").value(2));
    }

    @Test
    void getProtocolComplianceSummary_notFound() throws Exception {
        mockMvc.perform(get("/v1/protocols/00000000-0000-0000-0000-000000000000/compliance-summary"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProtocolPatients_returnsAllPatients() throws Exception {
        mockMvc.perform(get("/v1/protocols/550e8400-e29b-41d4-a716-446655440000/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void getProtocolPatients_filteredByStatus() throws Exception {
        mockMvc.perform(get("/v1/protocols/550e8400-e29b-41d4-a716-446655440000/patients")
                        .param("status", "at_risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].complianceCategory").value("at_risk"));
    }
}
