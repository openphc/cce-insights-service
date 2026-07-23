package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.openphc.cce.insights.service.AdoptionService;
import org.openphc.cce.insights.service.FacilityDirectory;
import org.openphc.cce.insights.web.controller.AdoptionController;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.dto.AdoptionKpiDto;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link AdoptionController} — the layer that makes RI-33 respect the global
 * filters. Locks: (1) date params route to the period-total range method (not today's snapshot)
 * and propagate verbatim, (2) the district filter narrows to the resolved facility set, (3) a
 * blank/absent district applies no scoping (even though the resolver mock defaults to an empty
 * list), (4) the facility filter narrows to one, and (5) the RI-33 field names serialize.
 */
@WebMvcTest(AdoptionController.class)
@Import(GlobalExceptionHandler.class)
class AdoptionControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdoptionService adoptionService;

    @MockitoBean
    private FacilityDirectory facilityDirectory;

    private static AdoptionKpiDto row(String facilityId, long expected, long actual) {
        return AdoptionKpiDto.builder()
                .facilityId(facilityId).facilityName(facilityId).district("")
                .expectedVisits(expected).actualVisits(actual)
                .adoptionRate(0.0).reportingGap(expected - actual)
                .build();
    }

    @Test
    void getAdoption_withDateRange_usesPeriodMethodAndPropagatesDatesAndRi33Fields() throws Exception {
        LocalDate start = LocalDate.of(2026, 4, 23);
        LocalDate end = LocalDate.of(2026, 7, 22);
        when(adoptionService.getAdoptionKpisByDateRange(start, end))
                .thenReturn(List.of(row("F-A", 910, 8)));

        mockMvc.perform(get("/v1/insights/facilities/adoption?startDate=2026-04-23&endDate=2026-07-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].expectedVisits").value(910))
                .andExpect(jsonPath("$.data[0].actualVisits").value(8))
                .andExpect(jsonPath("$.data[0].reportingGap").value(902));

        verify(adoptionService).getAdoptionKpisByDateRange(start, end);
        verify(adoptionService, never()).getAdoptionKpis();
    }

    @Test
    void getAdoption_withoutDates_usesTodaySnapshot() throws Exception {
        when(adoptionService.getAdoptionKpis()).thenReturn(List.of(row("F-A", 10, 1)));

        mockMvc.perform(get("/v1/insights/facilities/adoption"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(adoptionService).getAdoptionKpis();
        verify(adoptionService, never()).getAdoptionKpisByDateRange(any(), any());
    }

    /** District filter keeps only facilities the resolver reports for that district. */
    @Test
    void getAdoption_districtScoped_filtersOutOtherFacilities() throws Exception {
        when(adoptionService.getAdoptionKpisByDateRange(any(), any()))
                .thenReturn(List.of(row("F-A", 100, 50), row("F-B", 100, 20)));
        when(facilityDirectory.facilityIdsInDistrict("Gasabo")).thenReturn(List.of("F-A"));

        mockMvc.perform(get("/v1/insights/facilities/adoption?startDate=2026-04-23&endDate=2026-07-22&district=Gasabo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].facilityId").value("F-A"));
    }

    /** No district param = no scoping, even though the resolver mock defaults to an empty list. */
    @Test
    void getAdoption_noDistrict_returnsAllRows() throws Exception {
        when(adoptionService.getAdoptionKpisByDateRange(any(), any()))
                .thenReturn(List.of(row("F-A", 100, 50), row("F-B", 100, 20)));

        mockMvc.perform(get("/v1/insights/facilities/adoption?startDate=2026-04-23&endDate=2026-07-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getAdoption_facilityFilter_narrowsToThatFacility() throws Exception {
        when(adoptionService.getAdoptionKpisByDateRange(any(), any()))
                .thenReturn(List.of(row("F-A", 100, 50), row("F-B", 100, 20)));

        mockMvc.perform(get("/v1/insights/facilities/adoption?startDate=2026-04-23&endDate=2026-07-22&facilityId=F-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].facilityId").value("F-B"));
    }
}
