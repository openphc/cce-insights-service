package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.DashboardService;
import org.openphc.cce.insights.service.HandoffMetricsService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.DashboardComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.DashboardOverviewDto;
import org.openphc.cce.insights.web.dto.HandoffMetricDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/insights/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final HandoffMetricsService handoffMetricsService;

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
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DashboardComplianceSummaryDto summary = dashboardService.getComplianceSummary(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /** Deviations per hand-off (cce.insights.handoffs), for the hand-offs due within [startDate, endDate]. */
    @GetMapping("/handoffs")
    public ResponseEntity<ApiResponse<List<HandoffMetricDto>>> getHandoffs(
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        return ResponseEntity.ok(ApiResponse.ok(handoffMetricsService.getHandoffMetrics(startDate, endDate)));
    }
}
