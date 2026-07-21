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

import java.time.LocalDate;
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

    @MockitoBean
    private org.openphc.cce.insights.service.FacilityDirectory facilityDirectory;

    @Test
    void getFacilityRanking_returnsRankedList() throws Exception {
        when(facilityRankingService.getRankings(
                any(), nullable(String.class), nullable(LocalDate.class), nullable(LocalDate.class),
                eq("complianceRate"), eq("desc"), eq(50)))
                .thenReturn(List.of(FacilityRankingDto.builder()
                        .rank(1).facilityId("fac-1").complianceRate(95.0)
                        .totalEvents(20).totalEnrollments(5).activeDeviations(1).build()));

        mockMvc.perform(get("/v1/insights/facilities/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    /** District filter keeps only rows whose facility is in the selected district. */
    @Test
    void getFacilityRanking_districtScopedFiltersOutOtherFacilities() throws Exception {
        when(facilityRankingService.getRankings(
                any(), nullable(String.class), nullable(LocalDate.class), nullable(LocalDate.class),
                eq("complianceRate"), eq("desc"), eq(50)))
                .thenReturn(List.of(
                        FacilityRankingDto.builder().rank(1).facilityId("F-A").complianceRate(90.0).build(),
                        FacilityRankingDto.builder().rank(2).facilityId("F-B").complianceRate(80.0).build()));
        when(facilityDirectory.facilityIdsInDistrict("Gasabo")).thenReturn(List.of("F-A"));

        mockMvc.perform(get("/v1/insights/facilities/ranking?district=Gasabo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].facilityId").value("F-A"));
    }

    /** No district param = no scoping, even though the resolver mock would default to an empty list. */
    @Test
    void getFacilityRanking_noDistrictReturnsAllRows() throws Exception {
        when(facilityRankingService.getRankings(
                any(), nullable(String.class), nullable(LocalDate.class), nullable(LocalDate.class),
                eq("complianceRate"), eq("desc"), eq(50)))
                .thenReturn(List.of(
                        FacilityRankingDto.builder().rank(1).facilityId("F-A").complianceRate(90.0).build(),
                        FacilityRankingDto.builder().rank(2).facilityId("F-B").complianceRate(80.0).build()));

        mockMvc.perform(get("/v1/insights/facilities/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
