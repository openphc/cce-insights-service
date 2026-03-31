package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.ProtocolAnalyticsService;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/v1/protocols")
@RequiredArgsConstructor
public class ProtocolAnalyticsController {

    private final ProtocolAnalyticsService protocolAnalyticsService;

    @GetMapping("/{protocolDefinitionId}/step-analytics")
    public ResponseEntity<ApiResponse<StepAnalyticsDto>> getStepAnalytics(
            @PathVariable UUID protocolDefinitionId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        StepAnalyticsDto analytics = protocolAnalyticsService.getStepAnalytics(protocolDefinitionId);
        return ResponseEntity.ok(ApiResponse.ok(analytics));
    }

    @GetMapping("/{protocolDefinitionId}/completion-funnel")
    public ResponseEntity<ApiResponse<CompletionFunnelDto>> getCompletionFunnel(
            @PathVariable UUID protocolDefinitionId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        CompletionFunnelDto funnel = protocolAnalyticsService.getCompletionFunnel(protocolDefinitionId);
        return ResponseEntity.ok(ApiResponse.ok(funnel));
    }

    @GetMapping("/{protocolDefinitionId}/outcome-distribution")
    public ResponseEntity<ApiResponse<OutcomeDistributionDto>> getOutcomeDistribution(
            @PathVariable UUID protocolDefinitionId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        OutcomeDistributionDto distribution = protocolAnalyticsService.getOutcomeDistribution(
                protocolDefinitionId);
        return ResponseEntity.ok(ApiResponse.ok(distribution));
    }

    @GetMapping("/{protocolDefinitionId}/enrollment-trends")
    public ResponseEntity<ApiResponse<EnrollmentTrendDto>> getEnrollmentTrends(
            @PathVariable UUID protocolDefinitionId,
            @RequestParam(defaultValue = "weekly") String interval,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        EnrollmentTrendDto trends = protocolAnalyticsService.getEnrollmentTrends(
                protocolDefinitionId, interval, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(trends));
    }
}
