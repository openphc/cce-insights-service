package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.ProcessingQualityService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.ProcessingQualityDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/v1/insights/events")
@RequiredArgsConstructor
public class ProcessingQualityController {

    private final ProcessingQualityService processingQualityService;

    @GetMapping("/processing-quality")
    public ResponseEntity<ApiResponse<ProcessingQualityDto>> getProcessingQuality(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        ProcessingQualityDto quality = processingQualityService.getProcessingQuality(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(quality));
    }
}
