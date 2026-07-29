package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.FacilityActivityService;
import org.openphc.cce.insights.service.FacilityDirectory;
import org.openphc.cce.insights.web.controller.FacilityActivityController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.FacilityActivityItemDto;
import org.openphc.cce.insights.web.dto.FacilityActivitySummaryDto;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the facilityId/district precedence bug fixed alongside RI-62/RI-56:
 * facilityId must be honored even when a district is also present (checked first, not silently
 * overridden by the district branch), and a self-contradictory combination (facility not actually
 * in the given district) must return an all-zero summary rather than the district-only totals.
 */
@WebMvcTest(FacilityActivityController.class)
@Import(GlobalExceptionHandler.class)
class FacilityActivityControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityActivityService facilityActivityService;

    @MockitoBean
    private FacilityDirectory facilityDirectory;

    @Test
    void facilityId_only_usesSingleFacilitySummary() throws Exception {
        when(facilityDirectory.facilityIdsInDistrict(null)).thenReturn(null);
        when(facilityActivityService.getActivitySummaryForFacility(eq("11"), any(), any()))
                .thenReturn(FacilityActivitySummaryDto.builder()
                        .totalInScope(1).activeFacilities(1).inactiveFacilities(0).activeFacilityRate(100.0)
                        .build());

        mockMvc.perform(get("/v1/insights/facilities/activity-summary").param("facilityId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInScope").value(1))
                .andExpect(jsonPath("$.data.activeFacilities").value(1));
    }

    @Test
    void facilityId_takesPrecedenceOverDistrict_whenFacilityBelongsToIt() throws Exception {
        // Facility "11" genuinely belongs to Kigali (Demo) — combining both filters should still hit
        // the single-facility branch, not get silently overridden by the district branch.
        when(facilityDirectory.facilityIdsInDistrict("Kigali (Demo)")).thenReturn(List.of("11", "22"));
        when(facilityActivityService.getActivitySummaryForFacility(eq("11"), any(), any()))
                .thenReturn(FacilityActivitySummaryDto.builder()
                        .totalInScope(1).activeFacilities(1).inactiveFacilities(0).activeFacilityRate(100.0)
                        .build());

        mockMvc.perform(get("/v1/insights/facilities/activity-summary")
                        .param("facilityId", "11")
                        .param("district", "Kigali (Demo)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInScope").value(1))
                .andExpect(jsonPath("$.data.activeFacilities").value(1));
    }

    @Test
    void contradictoryFacilityAndDistrict_returnsAllZero() throws Exception {
        // Facility "11" does NOT belong to Gasabo — a self-contradictory combination must return
        // all-zero rather than the district-only totals (the pre-fix behaviour).
        when(facilityDirectory.facilityIdsInDistrict("Gasabo")).thenReturn(List.of("99"));

        mockMvc.perform(get("/v1/insights/facilities/activity-summary")
                        .param("facilityId", "11")
                        .param("district", "Gasabo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInScope").value(0))
                .andExpect(jsonPath("$.data.activeFacilities").value(0))
                .andExpect(jsonPath("$.data.inactiveFacilities").value(0))
                .andExpect(jsonPath("$.data.activeFacilityRate").value(0.0));
    }

    @Test
    void district_only_recomputesCountsOverDistrictScope() throws Exception {
        when(facilityDirectory.facilityIdsInDistrict("Gasabo")).thenReturn(List.of("1", "2"));
        when(facilityActivityService.getFacilityActivityDetail(any(), any())).thenReturn(List.of(
                FacilityActivityItemDto.builder().facilityId("1").facilityName("A").active(true).build(),
                FacilityActivityItemDto.builder().facilityId("2").facilityName("B").active(false).build(),
                // Out-of-district facility must not be counted.
                FacilityActivityItemDto.builder().facilityId("3").facilityName("C").active(true).build()));

        mockMvc.perform(get("/v1/insights/facilities/activity-summary").param("district", "Gasabo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInScope").value(2))
                .andExpect(jsonPath("$.data.activeFacilities").value(1))
                .andExpect(jsonPath("$.data.inactiveFacilities").value(1));
    }

    @Test
    void noFilters_fallsBackToTodaySummary() throws Exception {
        when(facilityDirectory.facilityIdsInDistrict(null)).thenReturn(null);
        when(facilityActivityService.getActivitySummary())
                .thenReturn(FacilityActivitySummaryDto.builder()
                        .totalInScope(10).activeFacilities(7).inactiveFacilities(3).activeFacilityRate(70.0)
                        .build());

        mockMvc.perform(get("/v1/insights/facilities/activity-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInScope").value(10))
                .andExpect(jsonPath("$.data.activeFacilities").value(7));
    }

    @Test
    void dateRange_noFilters_usesDateRangeSummary() throws Exception {
        when(facilityDirectory.facilityIdsInDistrict(null)).thenReturn(null);
        when(facilityActivityService.getActivitySummaryByDateRange(
                        eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(FacilityActivitySummaryDto.builder()
                        .totalInScope(5).activeFacilities(2).inactiveFacilities(3).activeFacilityRate(40.0)
                        .build());

        mockMvc.perform(get("/v1/insights/facilities/activity-summary")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInScope").value(5))
                .andExpect(jsonPath("$.data.activeFacilities").value(2));
    }
}
