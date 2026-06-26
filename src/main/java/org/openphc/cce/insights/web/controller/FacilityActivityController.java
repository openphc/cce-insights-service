package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.FacilityActivityService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.FacilityActivitySummaryDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/insights/facilities")
@RequiredArgsConstructor
public class FacilityActivityController {

    private final FacilityActivityService facilityActivityService;

    /**
     * GET /v1/insights/facilities/activity-summary
     *
     * With startDate+endDate: counts facilities with ≥1 successful HIE submission in the period.
     * With facilityId: reports the single-facility tile (1 in-scope; 1 active/inactive depending on
     *   whether that facility transmitted in the period).
     * Without filters: falls back to today's snapshot from mv_daily_facility_activity_summary.
     */
    @GetMapping("/activity-summary")
    public ResponseEntity<ApiResponse<FacilityActivitySummaryDto>> getActivitySummary(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        FacilityActivitySummaryDto dto;
        if (facilityId != null && !facilityId.isEmpty()) {
            LocalDate effectiveStart = startDate != null ? startDate
                    : endDate != null ? endDate
                    : LocalDate.now();
            LocalDate effectiveEnd   = endDate   != null ? endDate
                    : startDate != null ? startDate
                    : LocalDate.now();
            dto = facilityActivityService.getActivitySummaryForFacility(
                    facilityId, effectiveStart, effectiveEnd);
        } else if (startDate != null || endDate != null) {
            dto = facilityActivityService.getActivitySummaryByDateRange(
                    startDate != null ? startDate : LocalDate.now(),
                    endDate   != null ? endDate   : LocalDate.now());
        } else {
            dto = facilityActivityService.getActivitySummary();
        }
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }
}
