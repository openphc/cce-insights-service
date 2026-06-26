package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.AdoptionService;
import org.openphc.cce.insights.web.dto.AdoptionKpiDto;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.FacilityReferenceDto;
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
public class AdoptionController {

    private final AdoptionService adoptionService;

    /**
     * GET /v1/insights/facilities/adoption
     *
     * Per-facility e-Buzima adoption KPIs from mv_daily_adoption_kpis.
     * Sorted by reporting_gap DESC (worst under-reporters first).
     *
     * Without date params → today's snapshot (single-day adoption rate).
     * With startDate + endDate → multi-day aggregation per schema/07 formula:
     *   period_rate = sum(actual_patients) / (expected_per_day × days) × 100
     *   actualVisitsPerDay / reportingGapPerDay = daily averages over the range
     */
    @GetMapping("/adoption")
    public ResponseEntity<ApiResponse<List<AdoptionKpiDto>>> getAdoptionKpis(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        List<AdoptionKpiDto> result = (startDate != null || endDate != null)
                ? adoptionService.getAdoptionKpisByDateRange(
                        startDate != null ? startDate : LocalDate.now(),
                        endDate   != null ? endDate   : LocalDate.now())
                : adoptionService.getAdoptionKpis();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /v1/insights/facilities/reference
     *
     * The agreed facility list with expected patient volumes.
     * Sourced from facility (managed by programme staff via compliance service).
     * Used by admin screens for viewing and verifying the adoption baseline.
     */
    @GetMapping("/reference")
    public ResponseEntity<ApiResponse<List<FacilityReferenceDto>>> getFacilityReference() {
        return ResponseEntity.ok(ApiResponse.ok(adoptionService.getFacilityReference()));
    }
}
