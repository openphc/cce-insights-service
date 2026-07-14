package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Referrals KPI — total count of referral forms successfully received by HIE
 * plus a per-facility breakdown, filtered by inbound event {@code event_time}
 * (same clock every other page metric uses).
 */
@Data
@Builder
public class ReferralsKpiDto {

    /** Total accepted referral-initiated events across all facilities in scope. */
    private long totalReferralsReceived;

    /** Per-facility count, one entry per facility in the reference list (0 if none). */
    private List<FacilityReferralCountDto> byFacility;

    @Data
    @Builder
    public static class FacilityReferralCountDto {
        private String facilityId;
        private String facilityName;
        private long count;
    }
}
