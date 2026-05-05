package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.IntelligenceAnalyticsService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.IntelligenceSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/insights/intelligence")
@RequiredArgsConstructor
public class IntelligenceAnalyticsController {

    private final IntelligenceAnalyticsService intelligenceAnalyticsService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<IntelligenceSummaryDto>> getSummary() {
        IntelligenceSummaryDto summary = intelligenceAnalyticsService.getSummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
