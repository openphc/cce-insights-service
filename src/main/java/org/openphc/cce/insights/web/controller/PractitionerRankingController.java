package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.PractitionerRankingService;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.openphc.cce.insights.web.dto.PractitionerRankingDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/insights/practitioners")
@RequiredArgsConstructor
public class PractitionerRankingController {

    private final PractitionerRankingService practitionerRankingService;

    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<List<PractitionerRankingDto>>> getPractitionerRanking(
            @RequestParam(defaultValue = "complianceRate") String rankBy,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "50") int limit) {
        List<PractitionerRankingDto> rankings = practitionerRankingService.getRankings(rankBy, order, limit);
        return ResponseEntity.ok(ApiResponse.ok(rankings));
    }
}
