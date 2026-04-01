package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.FacilityRankingService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/insights/facilities")
@RequiredArgsConstructor
public class FacilityRankingController {

    private final FacilityRankingService facilityRankingService;

    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<List<FacilityRankingDto>>> getFacilityRanking(
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(defaultValue = "complianceRate") String rankBy,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        List<FacilityRankingDto> rankings = facilityRankingService.getRankings(
                startDate, endDate, rankBy, order, limit);
        return ResponseEntity.ok(ApiResponse.ok(rankings));
    }
}
