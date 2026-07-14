package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.DashboardService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.DashboardComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.DashboardOverviewDto;
import org.openphc.cce.insights.web.dto.ReferralsKpiDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/v1/insights/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<DashboardOverviewDto>> getOverview(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DashboardOverviewDto overview = dashboardService.getOverview(facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(overview));
    }

    @GetMapping("/compliance-summary")
    public ResponseEntity<ApiResponse<DashboardComplianceSummaryDto>> getComplianceSummary(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DashboardComplianceSummaryDto summary = dashboardService.getComplianceSummary(
                facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Referrals KPI — total count of referral forms successfully received by HIE
     * for the given date range, plus per-facility breakdown. Date range is applied
     * to inbound event {@code event_time} (same clock every other page metric uses).
     */
    @GetMapping("/referrals")
    public ResponseEntity<ApiResponse<ReferralsKpiDto>> getReferrals(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        ReferralsKpiDto kpi = dashboardService.getReferralsKpi(facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(kpi));
    }
}
