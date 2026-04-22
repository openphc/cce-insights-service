package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.ExportService;
import org.openphc.cce.insights.web.controller.ExportController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportController.class)
@Import(GlobalExceptionHandler.class)
class ExportControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @Test
    void exportJson_returnsJsonReport() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("patientId", "patient-1");
        row.put("protocolCanonical", "http://example.org/anc|1.0");
        row.put("complianceRate", 0.75);
        when(exportService.exportComplianceJson(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        mockMvc.perform(get("/v1/insights/exports/compliance-report").param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void exportCsv_returnsCsvReport() throws Exception {
        doAnswer(invocation -> {
            java.io.OutputStream os = invocation.getArgument(4);
            os.write("patient_id,protocol_canonical,compliance_rate\np1,anc|1.0,0.75\n".getBytes());
            return null;
        }).when(exportService).writeComplianceCsv(any(), any(), any(), any(), any());

        mockMvc.perform(get("/v1/insights/exports/compliance-report").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }
}
