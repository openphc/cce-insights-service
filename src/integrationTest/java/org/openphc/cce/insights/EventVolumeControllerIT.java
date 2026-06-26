package org.openphc.cce.insights;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.EventVolumeService;
import org.openphc.cce.insights.web.controller.EventVolumeController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.*;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventVolumeController.class)
@Import(GlobalExceptionHandler.class)
class EventVolumeControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventVolumeService eventVolumeService;

    @BeforeEach
    void setUp() {
        when(eventVolumeService.getSummary(any(), any(), any(), any()))
                .thenReturn(EventVolumeSummaryDto.builder()
                        .totalEvents(10)
                        .processingStatusBreakdown(Map.of(
                                "matched", EventVolumeSummaryDto.StatusCount.builder().count(8).percentage(80.0).build()))
                        .byFacility(List.of())
                        .bySource(List.of())
                        .build());

        when(eventVolumeService.getTrends(anyString(), any(), any(), any(), any()))
                .thenReturn(EventVolumeTrendDto.builder()
                        .interval("monthly")
                        .trends(List.of(EventVolumeTrendDto.TrendPoint.builder()
                                .period("2026-03").total(5).byResourceType(Map.of("Encounter", 5L)).build()))
                        .build());

        when(eventVolumeService.getByResourceType(any(), any(), any(), any()))
                .thenReturn(List.of(ResourceTypeCountDto.builder()
                        .resourceType("Encounter").count(10).build()));

        when(eventVolumeService.getByFacility(any(), any(), any(), any(), any()))
                .thenReturn(List.of(FacilityEventCountDto.builder()
                        .facilityId("fac-1").totalEvents(5).byResourceType(List.of()).build()));
    }

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
}
