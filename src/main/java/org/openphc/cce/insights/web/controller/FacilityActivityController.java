package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.FacilityActivityService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.FacilityActivityItemDto;
import org.openphc.cce.insights.web.dto.FacilityActivitySummaryDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

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
     * Without filters: falls back to today's active-facility count from mv_event_volume_hourly.
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

    /**
     * GET /v1/insights/facilities/activity-detail
     *
     * Drill-down behind the Active/Inactive facility cards (RI-29): every in-scope facility with
     * its active/inactive flag, district, and last-activity day for the selected range. The UI
     * partitions by {@code active}; counts reconcile with /activity-summary. Missing dates default
     * to today (same behaviour as the summary endpoint).
     */
    @GetMapping("/activity-detail")
    public ResponseEntity<ApiResponse<List<FacilityActivityItemDto>>> getActivityDetail(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : endDate != null ? endDate : LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : startDate != null ? startDate : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(
                facilityActivityService.getFacilityActivityDetail(start, end)));
    }
}
