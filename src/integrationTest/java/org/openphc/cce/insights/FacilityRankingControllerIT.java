package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.FacilityRankingService;
import org.openphc.cce.insights.web.controller.FacilityRankingController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FacilityRankingController.class)
@Import(GlobalExceptionHandler.class)
class FacilityRankingControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityRankingService facilityRankingService;

    @Test
    void getFacilityRanking_returnsRankedList() throws Exception {
        when(facilityRankingService.getRankings(any(), any(), any(), eq("complianceRate"), eq("desc"), eq(50)))
                .thenReturn(List.of(FacilityRankingDto.builder()
                        .rank(1).facilityId("fac-1").complianceRate(95.0)
                        .totalEvents(20).totalEnrollments(5).activeDeviations(1).build()));

        mockMvc.perform(get("/v1/insights/facilities/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
