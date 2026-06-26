package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.EventVolumeService;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/insights/events")
@RequiredArgsConstructor
public class EventVolumeController {

    private final EventVolumeService eventVolumeService;

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<EventKpiDto>> getEventKpis() {
        return ResponseEntity.ok(ApiResponse.ok(eventVolumeService.getEventKpis()));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<EventVolumeSummaryDto>> getSummary(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        EventVolumeSummaryDto summary = eventVolumeService.getSummary(facilityId, source, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/trends")
    public ResponseEntity<ApiResponse<EventVolumeTrendDto>> getTrends(
            @RequestParam(defaultValue = "weekly") String interval,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        EventVolumeTrendDto trends = eventVolumeService.getTrends(
                interval, startDate, endDate, facilityId, source);
        return ResponseEntity.ok(ApiResponse.ok(trends));
    }

    @GetMapping("/by-resource-type")
    public ResponseEntity<ApiResponse<List<ResourceTypeCountDto>>> getByResourceType(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        List<ResourceTypeCountDto> counts = eventVolumeService.getByResourceType(
                facilityId, source, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }

    @GetMapping("/by-facility")
    public ResponseEntity<ApiResponse<List<FacilityEventCountDto>>> getByFacility(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        List<FacilityEventCountDto> counts = eventVolumeService.getByFacility(
                facilityId, source, resourceType, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }
}
