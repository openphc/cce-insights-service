package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.FacilityActivityService;
import org.openphc.cce.insights.service.FacilityDirectory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/v1/insights/facilities")
@RequiredArgsConstructor
public class FacilityActivityController {

    private final FacilityActivityService facilityActivityService;
    private final FacilityDirectory facilityDirectory;

    /**
     * GET /v1/insights/facilities/activity-summary
     *
     * With facilityId: reports the single-facility tile (1 in-scope; 1 active/inactive depending on
     *   whether that facility transmitted in the period) — checked FIRST, since it's the most
     *   specific filter. If a district is ALSO given and the facility doesn't actually belong to it
     *   (a self-contradictory combination), returns all-zero rather than silently ignoring the
     *   facility filter — consistent with every other district+facility-filtered endpoint (e.g.
     *   FacilityRankingController), which AND the two together instead of one overriding the other.
     * With district (no facilityId): counts recomputed over just that district's facilities (from
     *   the detail list).
     * With startDate+endDate (no facilityId/district): counts facilities with ≥1 successful HIE
     *   submission in the period.
     * Without filters: falls back to today's active-facility count from mv_event_volume_hourly.
     */
    @GetMapping("/activity-summary")
    public ResponseEntity<ApiResponse<FacilityActivitySummaryDto>> getActivitySummary(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<String> districtIds = facilityDirectory.facilityIdsInDistrict(district);
        boolean hasDistrict = district != null && !district.isBlank() && districtIds != null;
        boolean hasFacility = facilityId != null && !facilityId.isEmpty();

        if (hasFacility) {
            if (hasDistrict && districtIds != null && !districtIds.contains(facilityId)) {
                return ResponseEntity.ok(ApiResponse.ok(FacilityActivitySummaryDto.builder()
                        .totalInScope(0).activeFacilities(0).inactiveFacilities(0).activeFacilityRate(0.0)
                        .build()));
            }
            LocalDate effectiveStart = startDate != null ? startDate
                    : endDate != null ? endDate
                    : LocalDate.now();
            LocalDate effectiveEnd   = endDate   != null ? endDate
                    : startDate != null ? startDate
                    : LocalDate.now();
            FacilityActivitySummaryDto dto = facilityActivityService.getActivitySummaryForFacility(
                    facilityId, effectiveStart, effectiveEnd);
            return ResponseEntity.ok(ApiResponse.ok(dto));
        }

        if (hasDistrict) {
            // District-scoped summary = counts recomputed over that district's facilities.
            LocalDate start = startDate != null ? startDate : endDate != null ? endDate : LocalDate.now();
            LocalDate end   = endDate   != null ? endDate   : startDate != null ? startDate : LocalDate.now();
            Set<String> scope = new HashSet<>(districtIds);
            List<FacilityActivityItemDto> items = facilityActivityService.getFacilityActivityDetail(start, end)
                    .stream().filter(f -> scope.contains(f.getFacilityId())).toList();
            long total = items.size();
            long active = items.stream().filter(f -> f.isActive()).count();
            double rate = total > 0 ? Math.round((double) active / total * 1000.0) / 10.0 : 0.0;
            return ResponseEntity.ok(ApiResponse.ok(FacilityActivitySummaryDto.builder()
                    .totalInScope(total)
                    .activeFacilities(active)
                    .inactiveFacilities(total - active)
                    .activeFacilityRate(rate)
                    .build()));
        }

        FacilityActivitySummaryDto dto;
        if (startDate != null || endDate != null) {
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
     * to today (same behaviour as the summary endpoint). Optionally scoped to one district.
     */
    @GetMapping("/activity-detail")
    public ResponseEntity<ApiResponse<List<FacilityActivityItemDto>>> getActivityDetail(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : endDate != null ? endDate : LocalDate.now();
        LocalDate end   = endDate   != null ? endDate   : startDate != null ? startDate : LocalDate.now();
        List<FacilityActivityItemDto> items = facilityActivityService.getFacilityActivityDetail(start, end);
        List<String> districtIds = facilityDirectory.facilityIdsInDistrict(district);
        if (district != null && !district.isBlank() && districtIds != null) {
            Set<String> scope = new HashSet<>(districtIds);
            items = items.stream().filter(f -> scope.contains(f.getFacilityId())).toList();
        }
        return ResponseEntity.ok(ApiResponse.ok(items));
    }
}
