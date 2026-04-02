package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.PatientRiskService;
import org.openphc.cce.insights.web.controller.PatientRiskController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.AtRiskHotspotDto;
import org.openphc.cce.insights.web.dto.RepeatDeviationPatientDto;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientRiskController.class)
@Import(GlobalExceptionHandler.class)
class PatientRiskControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientRiskService patientRiskService;

    @Test
    void getAtRiskHotspots_returnsHotspots() throws Exception {
        when(patientRiskService.getAtRiskHotspots(any(), any()))
                .thenReturn(List.of(AtRiskHotspotDto.builder()
                        .facilityId("fac-1").totalPatients(10)
                        .onTrack(AtRiskHotspotDto.CategoryCount.builder().count(7).percentage(70.0).build())
                        .atRisk(AtRiskHotspotDto.CategoryCount.builder().count(2).percentage(20.0).build())
                        .nonCompliant(AtRiskHotspotDto.CategoryCount.builder().count(1).percentage(10.0).build())
                        .build()));

        mockMvc.perform(get("/v1/insights/patients/at-risk-hotspots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getRepeatDeviations_returnsPatientsAboveThreshold() throws Exception {
        when(patientRiskService.getRepeatDeviationPatients(eq(1), any(), any()))
                .thenReturn(List.of(RepeatDeviationPatientDto.builder()
                        .patientId("p1").totalDeviations(3).overdueCount(2).missedCount(1)
                        .affectedProtocols(1).affectedSteps(2).build()));

        mockMvc.perform(get("/v1/insights/patients/repeat-deviations")
                        .param("minDeviations", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
