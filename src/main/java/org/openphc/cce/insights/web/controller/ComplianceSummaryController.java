package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.ComplianceSummaryService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.ComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.FacilitySummaryDto;
import org.openphc.cce.insights.web.dto.PatientComplianceDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ComplianceSummaryController {

    private final ComplianceSummaryService complianceSummaryService;

    @GetMapping("/protocols/{protocolDefinitionId}/compliance-summary")
    public ResponseEntity<ApiResponse<ComplianceSummaryDto>> getProtocolComplianceSummary(
            @PathVariable UUID protocolDefinitionId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        ComplianceSummaryDto summary = complianceSummaryService.getProtocolComplianceSummary(protocolDefinitionId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/facilities/{facilityId}/compliance-summary")
    public ResponseEntity<ApiResponse<FacilitySummaryDto>> getFacilityComplianceSummary(
            @PathVariable String facilityId,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        FacilitySummaryDto summary = complianceSummaryService.getFacilityComplianceSummary(facilityId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/protocols/{protocolDefinitionId}/patients")
    public ResponseEntity<ApiResponse<List<PatientComplianceDto>>> getProtocolPatients(
            @PathVariable UUID protocolDefinitionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String facilityId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        List<PatientComplianceDto> patients = complianceSummaryService.getProtocolPatients(
                protocolDefinitionId, status, limit);
        return ResponseEntity.ok(ApiResponse.ok(patients));
    }
}
