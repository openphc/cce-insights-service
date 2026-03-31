package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.IngestionAnalyticsService;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/v1/insights/ingestion")
@RequiredArgsConstructor
public class IngestionAnalyticsController {

    private final IngestionAnalyticsService ingestionAnalyticsService;

    @GetMapping("/funnel")
    public ResponseEntity<ApiResponse<IngestionFunnelDto>> getIngestionFunnel(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(required = false) String interval) {
        IngestionFunnelDto funnel = ingestionAnalyticsService.getIngestionFunnel(
                facilityId, source, startDate, endDate, interval);
        return ResponseEntity.ok(ApiResponse.ok(funnel));
    }

    @GetMapping("/rejections")
    public ResponseEntity<ApiResponse<RejectionAnalyticsDto>> getRejectionAnalytics(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        RejectionAnalyticsDto rejections = ingestionAnalyticsService.getRejectionAnalytics(
                facilityId, source, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(rejections));
    }

    @GetMapping("/source-quality")
    public ResponseEntity<ApiResponse<SourceDataQualityDto>> getSourceDataQuality(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        SourceDataQualityDto quality = ingestionAnalyticsService.getSourceDataQuality(
                facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(quality));
    }

    @GetMapping("/pipeline-loss")
    public ResponseEntity<ApiResponse<PipelineLossDto>> getPipelineLoss(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        PipelineLossDto loss = ingestionAnalyticsService.getPipelineLoss(
                facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(loss));
    }
}
