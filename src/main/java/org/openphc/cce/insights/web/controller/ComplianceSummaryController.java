package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.ComplianceSummaryService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.ComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.FacilitySummaryDto;
import org.openphc.cce.insights.web.dto.PaginationDto;
import org.openphc.cce.insights.web.dto.PatientComplianceDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/insights")
@RequiredArgsConstructor
public class ComplianceSummaryController {

    private final ComplianceSummaryService complianceSummaryService;

    @GetMapping("/protocols/compliance-summary")
    public ResponseEntity<ApiResponse<ComplianceSummaryDto>> getAllProtocolsComplianceSummary(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        ComplianceSummaryDto summary = complianceSummaryService.getAllProtocolsComplianceSummary(
                facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/protocols/{protocolDefinitionId}/compliance-summary")
    public ResponseEntity<ApiResponse<ComplianceSummaryDto>> getProtocolComplianceSummary(
            @PathVariable UUID protocolDefinitionId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        ComplianceSummaryDto summary = complianceSummaryService.getProtocolComplianceSummary(
                protocolDefinitionId, facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/facilities/{facilityId}/compliance-summary")
    public ResponseEntity<ApiResponse<FacilitySummaryDto>> getFacilityComplianceSummary(
            @PathVariable String facilityId,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        FacilitySummaryDto summary = complianceSummaryService.getFacilityComplianceSummary(
                facilityId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/protocols/{protocolDefinitionId}/patients")
    public ResponseEntity<ApiResponse<List<PatientComplianceDto>>> getProtocolPatients(
            @PathVariable UUID protocolDefinitionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "15") int limit,
            @RequestParam(required = false) String cursor) {
        int offset = 0;
        if (cursor != null && !cursor.isEmpty()) {
            try { offset = Integer.parseInt(cursor); } catch (NumberFormatException ignored) {}
        }
        var result = complianceSummaryService.getProtocolPatients(
                protocolDefinitionId, status, facilityId, patientId, startDate, endDate, limit, offset);
        long totalCount = result.totalCount();
        boolean hasMore = offset + result.patients().size() < totalCount;
        String nextCursor = hasMore ? String.valueOf(offset + limit) : null;
        var pagination = PaginationDto.builder()
                .limit(limit)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .build();
        return ResponseEntity.ok(ApiResponse.page(result.patients(), pagination));
    }
}
