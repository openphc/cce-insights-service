package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.ComplianceSummaryService;
import org.openphc.cce.insights.web.controller.ComplianceSummaryController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.*;

import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ComplianceSummaryController.class)
@Import(GlobalExceptionHandler.class)
class ComplianceSummaryControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplianceSummaryService complianceSummaryService;

    private static final UUID PROTOCOL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Test
    void getProtocolComplianceSummary_returnsAggregatedMetrics() throws Exception {
        when(complianceSummaryService.getProtocolComplianceSummary(eq(PROTOCOL_ID), any(), any()))
                .thenReturn(ComplianceSummaryDto.builder()
                        .protocolDefinitionId(PROTOCOL_ID)
                        .totalEnrollments(3)
                        .complianceRate(0.67)
                        .stepMetrics(ComplianceSummaryDto.StepMetrics.builder()
                                .totalSteps(9).completed(6).onTime(4).late(1).early(1)
                                .overdue(1).missed(1).pending(1).build())
                        .deviationCount(2)
                        .deviationBreakdown(Map.of("overdue", 1L, "missed", 1L, "orderViolation", 0L))
                        .build());

        mockMvc.perform(get("/v1/insights/protocols/550e8400-e29b-41d4-a716-446655440000/compliance-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protocolDefinitionId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.data.totalEnrollments").value(3))
                .andExpect(jsonPath("$.data.complianceRate").isNumber())
                .andExpect(jsonPath("$.data.stepMetrics.totalSteps").value(9))
                .andExpect(jsonPath("$.data.deviationCount").value(2));
    }

    @Test
    void getProtocolComplianceSummary_notFound() throws Exception {
        when(complianceSummaryService.getProtocolComplianceSummary(eq(UNKNOWN_ID), any(), any()))
                .thenThrow(new EntityNotFoundException("Protocol definition not found: " + UNKNOWN_ID));

        mockMvc.perform(get("/v1/insights/protocols/00000000-0000-0000-0000-000000000000/compliance-summary"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProtocolPatients_returnsAllPatients() throws Exception {
        when(complianceSummaryService.getProtocolPatients(eq(PROTOCOL_ID), isNull(), isNull(), eq(20), eq(0)))
                .thenReturn(new ProtocolPatientsPage(List.of(
                        PatientComplianceDto.builder().patientId("p1").complianceCategory("on_track").build(),
                        PatientComplianceDto.builder().patientId("p2").complianceCategory("at_risk").build(),
                        PatientComplianceDto.builder().patientId("p3").complianceCategory("non_compliant").build()), 3));

        mockMvc.perform(get("/v1/insights/protocols/550e8400-e29b-41d4-a716-446655440000/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.pagination.totalCount").value(3));
    }

    @Test
    void getProtocolPatients_filteredByStatus() throws Exception {
        when(complianceSummaryService.getProtocolPatients(eq(PROTOCOL_ID), eq("at_risk"), isNull(), eq(20), eq(0)))
                .thenReturn(new ProtocolPatientsPage(List.of(
                        PatientComplianceDto.builder().patientId("p2").complianceCategory("at_risk").build()), 1));

        mockMvc.perform(get("/v1/insights/protocols/550e8400-e29b-41d4-a716-446655440000/patients")
                        .param("status", "at_risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].complianceCategory").value("at_risk"))
                .andExpect(jsonPath("$.pagination.totalCount").value(1));
    }
}
