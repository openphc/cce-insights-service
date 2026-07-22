package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.FacilityDirectory;
import org.openphc.cce.insights.service.FacilityRankingService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.FacilityRankingDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/insights/facilities")
@RequiredArgsConstructor
public class FacilityRankingController {

    private final FacilityRankingService facilityRankingService;
    private final FacilityDirectory facilityDirectory;

    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<List<FacilityRankingDto>>> getFacilityRanking(
            @RequestParam(required = false) UUID protocolDefinitionId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "complianceRate") String rankBy,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        List<FacilityRankingDto> rankings = facilityRankingService.getRankings(
                protocolDefinitionId, facilityId, startDate, endDate, rankBy, order, limit);
        List<String> districtIds = facilityDirectory.facilityIdsInDistrict(district);
        if (district != null && !district.isBlank() && districtIds != null) {
            Set<String> scope = new HashSet<>(districtIds);
            rankings = rankings.stream().filter(r -> scope.contains(r.getFacilityId())).toList();
        }
        return ResponseEntity.ok(ApiResponse.ok(rankings));
    }
}
