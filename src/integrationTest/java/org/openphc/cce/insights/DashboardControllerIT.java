package org.openphc.cce.insights;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.service.DashboardService;
import org.openphc.cce.insights.web.GlobalExceptionHandler;
import org.openphc.cce.insights.web.controller.DashboardController;
import org.openphc.cce.insights.web.dto.ReferralsKpiDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(GlobalExceptionHandler.class)
class DashboardControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void getReferrals_returnsTotalAndFacilityBreakdown() throws Exception {
        ReferralsKpiDto dto = ReferralsKpiDto.builder()
                .totalReferralsReceived(7L)
                .byFacility(List.of(
                        ReferralsKpiDto.FacilityReferralCountDto.builder()
                                .facilityId("0002").facilityName("Kigali South HC").count(5L).build(),
                        ReferralsKpiDto.FacilityReferralCountDto.builder()
                                .facilityId("0015").facilityName("Nyarugenge HC").count(2L).build(),
                        ReferralsKpiDto.FacilityReferralCountDto.builder()
                                .facilityId("0099").facilityName("Silent HC").count(0L).build()))
                .build();
        when(dashboardService.getReferralsKpi(isNull(), any(), any())).thenReturn(dto);

        mockMvc.perform(get("/v1/insights/dashboard/referrals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReferralsReceived").value(7))
                .andExpect(jsonPath("$.data.byFacility[0].facilityId").value("0002"))
                .andExpect(jsonPath("$.data.byFacility[0].facilityName").value("Kigali South HC"))
                .andExpect(jsonPath("$.data.byFacility[0].count").value(5))
                .andExpect(jsonPath("$.data.byFacility[2].count").value(0));
    }

    @Test
    void getReferrals_facilityScopedReturnsSingleRow() throws Exception {
        ReferralsKpiDto dto = ReferralsKpiDto.builder()
                .totalReferralsReceived(3L)
                .byFacility(List.of(
                        ReferralsKpiDto.FacilityReferralCountDto.builder()
                                .facilityId("0002").facilityName("Kigali South HC").count(3L).build()))
                .build();
        when(dashboardService.getReferralsKpi(eq("0002"), any(), any())).thenReturn(dto);

        mockMvc.perform(get("/v1/insights/dashboard/referrals?facilityId=0002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReferralsReceived").value(3))
                .andExpect(jsonPath("$.data.byFacility.length()").value(1))
                .andExpect(jsonPath("$.data.byFacility[0].facilityId").value("0002"));
    }
}
