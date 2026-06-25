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
@RequestMapping("/v1/insights")
@RequiredArgsConstructor
public class DeviationController {

    private final DeviationAnalyticsService deviationAnalyticsService;

    @GetMapping("/deviations/kpis")
    public ResponseEntity<ApiResponse<DeviationKpiDto>> getDeviationKpis(
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        return ResponseEntity.ok(ApiResponse.ok(
                deviationAnalyticsService.getDeviationKpis(protocolDefinitionId, startDate, endDate)));
    }

    @GetMapping("/deviations")
    public ResponseEntity<ApiResponse<List<DeviationDto>>> getDeviations(
            @RequestParam(required = false) String deviationType,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit) {
        List<DeviationDto> deviations = deviationAnalyticsService.getDeviations(
                deviationType, facilityId, protocolDefinitionId, startDate, endDate, limit);
        return ResponseEntity.ok(ApiResponse.ok(deviations));
    }

    @GetMapping("/deviations/trends")
    public ResponseEntity<ApiResponse<DeviationTrendDto>> getDeviationTrends(
            @RequestParam(defaultValue = "weekly") String interval,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DeviationTrendDto trends = deviationAnalyticsService.getDeviationTrends(
                interval, startDate, endDate, facilityId, protocolDefinitionId);
        return ResponseEntity.ok(ApiResponse.ok(trends));
    }

    @GetMapping("/deviations/intelligence-summary")
    public ResponseEntity<ApiResponse<DeviationIntelligenceSummaryDto>> getIntelligenceSummary(
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(required = false) String facilityId) {
        DeviationIntelligenceSummaryDto summary = deviationAnalyticsService.getIntelligenceSummary(
                startDate, endDate, facilityId);
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
