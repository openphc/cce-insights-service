package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Referrals KPI — referrals received by HIE plus a compliant / non-compliant split and a
 * per-facility breakdown, filtered by inbound event {@code event_time} (same clock every other
 * page metric uses).
 *
 * <p>Wording: a "compliant" referral is one matched to (completed) a Referral step in a tracked
 * care journey; "non-compliant" is a received referral not matched to a journey (received - matched).
 * The compliance rate is compliant / received.
 */
@Data
@Builder
public class ReferralsKpiDto {

    /** Total referrals received by HIE across all facilities in scope. */
    private long totalReferralsReceived;

    /** Of those received, the ones matched to a Referral step ("compliant"). */
    private long compliantReferrals;

    /** Received referrals not matched to a tracked care journey ("non-compliant") = received - matched. */
    private long nonCompliantReferrals;

    /** Compliant as a percentage of received (0 when none received). */
    private double referralComplianceRate;

    /** Per-facility breakdown, one entry per facility in the reference list (0 if none). */
    private List<FacilityReferralCountDto> byFacility;

    @Data
    @Builder
    public static class FacilityReferralCountDto {
        private String facilityId;
        private String facilityName;
        /** Facility's district (may be empty). Enables the district filter in the drill-down. */
        private String district;
        /** Referrals received by HIE for this facility. */
        private long count;
        /** Compliant (matched) referrals for this facility. */
        private long compliant;
        /** Non-compliant (received - matched) referrals for this facility. */
        private long nonCompliant;
        /** Compliant as a percentage of received for this facility (0 when none received). */
        private double complianceRate;
    }
}
