package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/**
 * One patient behind the "Referrals Received by HIE" indicator (RI-44 patient drill-down).
 * facilityId/facilityName is the reporting (origin) facility; matchedCount ≤ referralCount are the
 * referrals matched to a Referral step ("compliant").
 */
@Data
@Builder
public class PatientReferralDto {
    private String patientId;
    private String facilityId;
    private String facilityName;
    private String lastReferral;   // ISO datetime of the patient's most recent referral event
    private long referralCount;
    private long matchedCount;
}
