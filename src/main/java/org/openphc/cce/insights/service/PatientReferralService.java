package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.PatientReferralDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RI-44 — patient-level referral indicators for the Patients page. Only "Referrals Received by HIE"
 * is data-backed today (event_time-scoped, one row per distinct patient); "Created" and "Failed"
 * indicators are UI placeholders pending a product definition, so they are not served here.
 */
@Service
@RequiredArgsConstructor
public class PatientReferralService {

    private final InboundEventRepository inboundEventRepository;
    private final DailyKpiRepository dailyKpiRepository;

    @Cacheable(value = "analytics",
            key = "'patient-referrals-received-' + (#startDate ?: 'all') + '-' + (#endDate ?: 'all')")
    public List<PatientReferralDto> getReferralsReceivedByHie(OffsetDateTime startDate, OffsetDateTime endDate) {
        // facility_id → name (reference table), so the drill-down shows the reporting facility name.
        Map<String, String> facilityNames = new LinkedHashMap<>();
        for (Object[] ref : dailyKpiRepository.getFacilityReference()) {
            facilityNames.put((String) ref[0], (String) ref[1]);
        }

        List<PatientReferralDto> out = new ArrayList<>();
        for (Object[] r : inboundEventRepository.referralsReceivedByHIEByPatient(startDate, endDate)) {
            String facilityId = (String) r[1];
            out.add(PatientReferralDto.builder()
                    .patientId((String) r[0])
                    .facilityId(facilityId)
                    .facilityName(facilityNames.getOrDefault(facilityId, facilityId))
                    .lastReferral((String) r[2])
                    .referralCount(((Number) r[3]).longValue())
                    .matchedCount(((Number) r[4]).longValue())
                    .build());
        }
        return out;
    }
}
