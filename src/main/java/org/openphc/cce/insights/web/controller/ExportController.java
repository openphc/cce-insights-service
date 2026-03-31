package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.ExportService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/compliance-report")
    public ResponseEntity<?> exportComplianceReport(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {

        if ("csv".equalsIgnoreCase(format)) {
            StreamingResponseBody body = exportService.exportComplianceCsv(
                    protocolDefinitionId, facilityId, startDate, endDate);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header("Content-Disposition", "attachment; filename=compliance-report.csv")
                    .body(body);
        }

        List<Map<String, Object>> data = exportService.exportComplianceJson(
                protocolDefinitionId, facilityId, startDate, endDate);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.ok(data));
    }
}
