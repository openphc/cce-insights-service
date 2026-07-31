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
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        return ResponseEntity.ok(ApiResponse.ok(
                deviationAnalyticsService.getDeviationKpis(
                        protocolDefinitionId, facilityId, district, startDate, endDate)));
    }

    @GetMapping("/deviations")
    public ResponseEntity<ApiResponse<List<DeviationDto>>> getDeviations(
            @RequestParam(required = false) String deviationType,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit) {
        List<DeviationDto> deviations = deviationAnalyticsService.getDeviations(
                deviationType, facilityId, district, protocolDefinitionId, startDate, endDate, limit);
        return ResponseEntity.ok(ApiResponse.ok(deviations));
    }

    @GetMapping("/deviations/trends")
    public ResponseEntity<ApiResponse<DeviationTrendDto>> getDeviationTrends(
            @RequestParam(defaultValue = "weekly") String interval,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DeviationTrendDto trends = deviationAnalyticsService.getDeviationTrends(
                interval, startDate, endDate, facilityId, district, protocolDefinitionId);
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

    // deviationType -> internal sort column. Reuses the same OVERDUE/MISSED/ORDER_VIOLATION values
    // every other deviations endpoint already accepts, rather than exposing raw SQL column names.
    private static String sortColumnFor(String deviationType) {
        if (deviationType == null) return "total_deviations";
        return switch (deviationType) {
            case "OVERDUE" -> "overdue_count";
            case "MISSED" -> "missed_count";
            case "ORDER_VIOLATION" -> "order_violation_count";
            default -> "total_deviations";
        };
    }

    @GetMapping("/deviations/by-facility")
    public ResponseEntity<ApiResponse<List<DeviationByFacilityDto>>> getDeviationsByFacility(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(required = false) String deviationType,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        int offset = 0;
        if (cursor != null && !cursor.isEmpty()) {
            try { offset = Integer.parseInt(cursor); } catch (NumberFormatException ignored) {}
        }
        var result = deviationAnalyticsService.getDeviationsByFacility(
                facilityId, district, protocolDefinitionId, startDate, endDate, sortColumnFor(deviationType), limit, offset);
        long totalCount = result.totalCount();
        boolean hasMore = offset + result.facilities().size() < totalCount;
        String nextCursor = hasMore ? String.valueOf(offset + limit) : null;
        var pagination = PaginationDto.builder()
                .limit(limit)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .build();
        return ResponseEntity.ok(ApiResponse.page(result.facilities(), pagination));
    }

    @GetMapping("/deviations/by-action")
    public ResponseEntity<ApiResponse<List<DeviationByActionDto>>> getDeviationsByAction(
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        List<DeviationByActionDto> results = deviationAnalyticsService.getDeviationsByAction(
                protocolDefinitionId, facilityId, district, startDate, endDate);
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
