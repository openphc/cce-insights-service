package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.DashboardService;
import org.openphc.cce.insights.service.FacilityDirectory;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.DashboardComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.DashboardOverviewDto;
import org.openphc.cce.insights.web.dto.ReferralsKpiDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/v1/insights/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final FacilityDirectory facilityDirectory;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<DashboardOverviewDto>> getOverview(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DashboardOverviewDto overview = dashboardService.getOverview(facilityId, district, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(overview));
    }

    @GetMapping("/compliance-summary")
    public ResponseEntity<ApiResponse<DashboardComplianceSummaryDto>> getComplianceSummary(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        DashboardComplianceSummaryDto summary = dashboardService.getComplianceSummary(
                facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Referrals KPI — total count of referral forms successfully received by HIE
     * for the given date range, plus per-facility breakdown. Date range is applied
     * to inbound event {@code event_time} (same clock every other page metric uses).
     */
    @GetMapping("/referrals")
    public ResponseEntity<ApiResponse<ReferralsKpiDto>> getReferrals(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        ReferralsKpiDto kpi = dashboardService.getReferralsKpi(facilityId, startDate, endDate);
        List<String> districtIds = facilityDirectory.facilityIdsInDistrict(district);
        if (district != null && !district.isBlank() && districtIds != null && kpi.getByFacility() != null) {
            Set<String> scope = new HashSet<>(districtIds);
            List<ReferralsKpiDto.FacilityReferralCountDto> rows = kpi.getByFacility().stream()
                    .filter(r -> scope.contains(r.getFacilityId())).toList();
            long received = rows.stream().mapToLong(ReferralsKpiDto.FacilityReferralCountDto::getCount).sum();
            long compliant = rows.stream().mapToLong(ReferralsKpiDto.FacilityReferralCountDto::getCompliant).sum();
            kpi = ReferralsKpiDto.builder()
                    .totalReferralsReceived(received)
                    .compliantReferrals(compliant)
                    .nonCompliantReferrals(received - compliant)
                    .referralComplianceRate(received > 0 ? Math.round((double) compliant / received * 1000.0) / 10.0 : 0.0)
                    .byFacility(rows)
                    .build();
        }
        return ResponseEntity.ok(ApiResponse.ok(kpi));
    }
}
