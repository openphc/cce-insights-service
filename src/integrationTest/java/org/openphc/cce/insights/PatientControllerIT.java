package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PatientControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getComplianceTimeline_returnsTimeline() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/compliance-timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value("260225-0002-5501"))
                .andExpect(jsonPath("$.data.protocols").isArray())
                .andExpect(jsonPath("$.data.protocols[0].timeline").isArray());
    }

    @Test
    void getProtocolTracking_returnsList() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/protocol-tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getProtocolTrackingDetail_returnsStepsAndDeviations() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5502/protocol-tracking/660e8400-e29b-41d4-a716-446655440002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protocolInstanceId").value("660e8400-e29b-41d4-a716-446655440002"))
                .andExpect(jsonPath("$.data.steps").isArray())
                .andExpect(jsonPath("$.data.steps.length()").value(3))
                .andExpect(jsonPath("$.data.deviations").isArray())
                .andExpect(jsonPath("$.data.deviations.length()").value(1));
    }

    @Test
    void getPatientEvents_returnsEventHistory() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].eventId").isString())
                .andExpect(jsonPath("$.data[0].type").isString())
                .andExpect(jsonPath("$.data[0].resourceType").isString())
                .andExpect(jsonPath("$.data[0].processingStatus").isString());
    }

    @Test
    void getPatientEvents_filteredByResourceType() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/events")
                        .param("resourceType", "Encounter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getPatientDeviations_returnsDeviationHistory() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5502/deviations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deviationType").value("OVERDUE"))
                .andExpect(jsonPath("$.data[0].protocolCanonical").isString());
    }

    @Test
    void getPatientDeviations_filteredByType() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5503/deviations")
                        .param("deviationType", "MISSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getPatientDeviations_emptyForCompliantPatient() throws Exception {
        mockMvc.perform(get("/v1/insights/patients/260225-0002-5501/deviations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
