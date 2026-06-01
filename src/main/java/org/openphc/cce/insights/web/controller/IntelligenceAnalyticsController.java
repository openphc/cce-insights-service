package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.IntelligenceAnalyticsService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.IntelligenceSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/v1/insights/intelligence")
@RequiredArgsConstructor
public class IntelligenceAnalyticsController {

    private final IntelligenceAnalyticsService intelligenceAnalyticsService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<IntelligenceSummaryDto>> getSummary(
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        IntelligenceSummaryDto summary = intelligenceAnalyticsService.getSummary(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
