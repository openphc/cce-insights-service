package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.PatientRiskService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.AtRiskHotspotDto;
import org.openphc.cce.insights.web.dto.RepeatDeviationPatientDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/insights/patients")
@RequiredArgsConstructor
public class PatientRiskController {

    private final PatientRiskService patientRiskService;

    @GetMapping("/at-risk-hotspots")
    public ResponseEntity<ApiResponse<List<AtRiskHotspotDto>>> getAtRiskHotspots(
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        List<AtRiskHotspotDto> hotspots = patientRiskService.getAtRiskHotspots(
                protocolDefinitionId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(hotspots));
    }

    @GetMapping("/repeat-deviations")
    public ResponseEntity<ApiResponse<List<RepeatDeviationPatientDto>>> getRepeatDeviations(
            @RequestParam(defaultValue = "3") int minDeviations,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        List<RepeatDeviationPatientDto> patients = patientRiskService.getRepeatDeviationPatients(
                minDeviations, facilityId, protocolDefinitionId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(patients));
    }
}
