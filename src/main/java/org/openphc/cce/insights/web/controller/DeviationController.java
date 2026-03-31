package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.DeviationAnalyticsService;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class DeviationController {

    private final DeviationAnalyticsService deviationAnalyticsService;

    @GetMapping("/deviations/trends")
    public ResponseEntity<ApiResponse<DeviationTrendDto>> getDeviationTrends(
            @RequestParam(defaultValue = "weekly") String interval,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DeviationTrendDto trends = deviationAnalyticsService.getDeviationTrends(
                interval, startDate, endDate, facilityId);
        return ResponseEntity.ok(ApiResponse.ok(trends));
    }

    @GetMapping("/intelligence/summary")
    public ResponseEntity<ApiResponse<IntelligenceSummaryDto>> getIntelligenceSummary() {
        IntelligenceSummaryDto summary = deviationAnalyticsService.getIntelligenceSummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/deviations/by-action")
    public ResponseEntity<ApiResponse<List<DeviationByActionDto>>> getDeviationsByAction(
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        List<DeviationByActionDto> results = deviationAnalyticsService.getDeviationsByAction(
                protocolDefinitionId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @GetMapping("/deviations/resolution-rate")
    public ResponseEntity<ApiResponse<DeviationResolutionDto>> getResolutionRate(
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DeviationResolutionDto result = deviationAnalyticsService.getResolutionRate(
                protocolDefinitionId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
