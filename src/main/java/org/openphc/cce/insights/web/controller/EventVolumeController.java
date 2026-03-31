package org.openphc.cce.insights.web.controller;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.service.EventVolumeService;
import org.openphc.cce.insights.web.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
public class EventVolumeController {

    private final EventVolumeService eventVolumeService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<EventVolumeSummaryDto>> getSummary(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        EventVolumeSummaryDto summary = eventVolumeService.getSummary(startDate, endDate);
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
                interval, startDate, endDate, facilityId);
        return ResponseEntity.ok(ApiResponse.ok(trends));
    }

    @GetMapping("/by-resource-type")
    public ResponseEntity<ApiResponse<List<ResourceTypeCountDto>>> getByResourceType(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        List<ResourceTypeCountDto> counts = eventVolumeService.getByResourceType(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }

    @GetMapping("/by-facility")
    public ResponseEntity<ApiResponse<List<FacilityEventCountDto>>> getByFacility(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        List<FacilityEventCountDto> counts = eventVolumeService.getByFacility(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }

    @GetMapping("/by-practitioner")
    public ResponseEntity<ApiResponse<List<PractitionerEventCountDto>>> getByPractitioner(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        List<PractitionerEventCountDto> counts = eventVolumeService.getByPractitioner(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }

    @GetMapping("/by-source")
    public ResponseEntity<ApiResponse<List<SourceSystemCountDto>>> getBySource(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate) {
        List<SourceSystemCountDto> counts = eventVolumeService.getBySource(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }

    @GetMapping("/source-comparison")
    public ResponseEntity<ApiResponse<SourceComparisonDto>> compareSourceSystems(
            @RequestParam String sourceA,
            @RequestParam String sourceB,
            @RequestParam(defaultValue = "300") long windowSeconds,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "20") int sampleLimit) {
        SourceComparisonDto result = eventVolumeService.compareSourceSystems(
                sourceA, sourceB, windowSeconds, facilityId, startDate, endDate,
                Math.min(sampleLimit, 100));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
