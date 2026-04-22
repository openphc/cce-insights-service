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
        when(eventVolumeService.getSummary(any(), any()))
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

        when(eventVolumeService.getByResourceType(any(), any()))
                .thenReturn(List.of(ResourceTypeCountDto.builder()
                        .resourceType("Encounter").count(10).build()));

        when(eventVolumeService.getByFacility(any(), any()))
                .thenReturn(List.of(FacilityEventCountDto.builder()
                        .facilityId("fac-1").totalEvents(5).byResourceType(List.of()).build()));

        when(eventVolumeService.getByPractitioner(any(), any()))
                .thenReturn(List.of(PractitionerEventCountDto.builder()
                        .practitionerRef("prac-1").totalEvents(3).byResourceType(List.of()).build()));

        when(eventVolumeService.getBySource(any(), any()))
                .thenReturn(List.of(SourceSystemCountDto.builder()
                        .source("ebuzima-direct").totalEvents(5).byResourceType(List.of()).build()));

        when(eventVolumeService.compareSourceSystems(eq("ebuzima/kigali-south"), eq("rhie-mediator"),
                eq(300L), any(), any(), any(), anyInt()))
                .thenReturn(SourceComparisonDto.builder()
                        .sourceA("ebuzima/kigali-south").sourceB("rhie-mediator").matchWindowSeconds(300)
                        .sourceASummary(SourceComparisonDto.SourceSummary.builder()
                                .source("ebuzima/kigali-south").totalEvents(8).uniqueEvents(2)
                                .overlappingEvents(6).overlapPercentage(75.0).uniqueByResourceType(List.of()).build())
                        .sourceBSummary(SourceComparisonDto.SourceBSummary.builder()
                                .source("rhie-mediator").totalEvents(7).uniqueEvents(1)
                                .overlappingEvents(6).overlapPercentage(85.7).uniqueByResourceType(List.of()).build())
                        .overlap(SourceComparisonDto.OverlapSummary.builder()
                                .totalOverlappingEvents(6)
                                .byResourceType(List.of(SourceComparisonDto.ResourceTypeCount.builder()
                                        .resourceType("Encounter").count(4).build())).build())
                        .build());
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
