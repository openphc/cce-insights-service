package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.ProcessingQualityService;
import org.openphc.cce.insights.web.controller.ProcessingQualityController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.ProcessingQualityDto;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProcessingQualityController.class)
@Import(GlobalExceptionHandler.class)
class ProcessingQualityControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcessingQualityService processingQualityService;

    @Test
    void getProcessingQuality_returnsQualityMetrics() throws Exception {
        when(processingQualityService.getProcessingQuality(any(), any()))
                .thenReturn(ProcessingQualityDto.builder()
                        .totalEvents(10)
                        .overall(Map.of("matched", ProcessingQualityDto.StatusDetail.builder()
                                .count(8).percentage(80.0).build()))
                        .bySource(List.of())
                        .build());

        mockMvc.perform(get("/v1/insights/events/processing-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvents").isNumber())
                .andExpect(jsonPath("$.data.overall").isMap());
    }
}
