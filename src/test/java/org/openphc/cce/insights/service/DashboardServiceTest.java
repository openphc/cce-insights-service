package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.DashboardComplianceSummaryDto;
import org.openphc.cce.insights.web.dto.ReferralsKpiDto;
import org.openphc.cce.insights.web.dto.ReferralsKpiDto.FacilityReferralCountDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardService#getReferralsKpi} (RI-35) — the compliant / non-compliant /
 * rate split layered on mv_daily_referral_kpis. "Compliant" = a received referral matched to a
 * Referral step; non-compliant = received − matched; rate = compliant ÷ received. Locks the totals,
 * the per-facility breakdown (anchored to the reference list with district), the 0-when-none-received
 * rate, and single-facility scoping. Repositories are mocked (no ClickHouse).
 */
class DashboardServiceTest {

    private final InboundEventRepository inbound = mock(InboundEventRepository.class);
    private final DailyKpiRepository dailyKpi = mock(DailyKpiRepository.class);
    private final DeviationRepository deviation = mock(DeviationRepository.class);
    private final PractitionerRankingService practitioners = mock(PractitionerRankingService.class);
    private final DashboardService service = new DashboardService(
            inbound,
            deviation,
            dailyKpi,
            mock(FacilityRankingService.class),
            practitioners,
            mock(DeviationAnalyticsService.class));

    /** grouped referral row: [facility_id, received, matched]. */
    private static Object[] grp(String id, long received, long matched) {
        return new Object[]{id, received, matched};
    }

    /** facility reference row: [facility_id, facility_name, expected_per_day, district]. */
    private static Object[] ref(String id, String name, String district) {
        return new Object[]{id, name, 0L, district};
    }

    private static Map<String, FacilityReferralCountDto> byId(List<FacilityReferralCountDto> rows) {
        return rows.stream().collect(Collectors.toMap(FacilityReferralCountDto::getFacilityId, r -> r));
    }

    @Test
    void getReferralsKpi_computesTotalsCompliantSplitAndRate() {
        when(inbound.countReferralsReceivedByHIEGroupedByFacility(any(), any()))
                .thenReturn(List.of(grp("F-A", 5, 3), grp("F-B", 4, 4)));
        when(inbound.countReferralsReceivedByHIE(any(), any(), any())).thenReturn(9L);
        when(inbound.countReferralsMatched(any(), any(), any())).thenReturn(7L);
        when(dailyKpi.getFacilityReference()).thenReturn(List.of(
                ref("F-A", "Alpha", "North"),
                ref("F-B", "Bravo", "South")));

        ReferralsKpiDto dto = service.getReferralsKpi(null, null, null);

        assertThat(dto.getTotalReferralsReceived()).isEqualTo(9);
        assertThat(dto.getCompliantReferrals()).isEqualTo(7);
        assertThat(dto.getNonCompliantReferrals()).isEqualTo(2);        // 9 - 7
        assertThat(dto.getReferralComplianceRate()).isEqualTo(77.8);    // round(7/9*100, 1dp)
    }

    @Test
    void getReferralsKpi_perFacilityCarriesDistrictAndDerivesSplit() {
        when(inbound.countReferralsReceivedByHIEGroupedByFacility(any(), any()))
                .thenReturn(List.of(grp("F-A", 5, 3), grp("F-B", 4, 4)));
        when(inbound.countReferralsReceivedByHIE(any(), any(), any())).thenReturn(9L);
        when(inbound.countReferralsMatched(any(), any(), any())).thenReturn(7L);
        // F-C is in the reference but has no referrals → must still appear with zeros.
        when(dailyKpi.getFacilityReference()).thenReturn(List.of(
                ref("F-A", "Alpha", "North"),
                ref("F-B", "Bravo", "South"),
                ref("F-C", "Charlie", "North")));

        Map<String, FacilityReferralCountDto> rows = byId(service.getReferralsKpi(null, null, null).getByFacility());

        assertThat(rows).containsOnlyKeys("F-A", "F-B", "F-C");
        assertThat(rows.get("F-A").getDistrict()).isEqualTo("North");
        assertThat(rows.get("F-A").getCount()).isEqualTo(5);
        assertThat(rows.get("F-A").getCompliant()).isEqualTo(3);
        assertThat(rows.get("F-A").getNonCompliant()).isEqualTo(2);
        assertThat(rows.get("F-A").getComplianceRate()).isEqualTo(60.0);
        // Fully compliant facility.
        assertThat(rows.get("F-B").getNonCompliant()).isZero();
        assertThat(rows.get("F-B").getComplianceRate()).isEqualTo(100.0);
        // No-referral facility from the reference list.
        FacilityReferralCountDto c = rows.get("F-C");
        assertThat(c.getCount()).isZero();
        assertThat(c.getCompliant()).isZero();
        assertThat(c.getNonCompliant()).isZero();
        assertThat(c.getComplianceRate()).isEqualTo(0.0);
    }

    @Test
    void getReferralsKpi_rateIsZeroWhenNothingReceived() {
        when(inbound.countReferralsReceivedByHIEGroupedByFacility(any(), any())).thenReturn(List.of());
        when(inbound.countReferralsReceivedByHIE(any(), any(), any())).thenReturn(0L);
        when(inbound.countReferralsMatched(any(), any(), any())).thenReturn(0L);
        when(dailyKpi.getFacilityReference()).thenReturn(List.<Object[]>of(ref("F-A", "Alpha", "North")));

        ReferralsKpiDto dto = service.getReferralsKpi(null, null, null);

        assertThat(dto.getTotalReferralsReceived()).isZero();
        assertThat(dto.getReferralComplianceRate()).isEqualTo(0.0);   // no divide-by-zero
        assertThat(dto.getByFacility()).hasSize(1);
        assertThat(dto.getByFacility().get(0).getComplianceRate()).isEqualTo(0.0);
    }

    /** activity tile row: [totalInScope, activeFacilities, inactiveFacilities, activeFacilityRate]. */
    private static Object[] activity(long total, long active, long inactive, double rate) {
        return new Object[]{total, active, inactive, rate};
    }

    /**
     * RI-36 — the patient block is the matched-event cohort (tracked), the deviation-occurrence
     * intersection (non-compliant), compliant = tracked − non-compliant, rate = compliant ÷ tracked.
     * No enrolled_at anywhere. Practitioner rankings are empty here to isolate the patient math.
     */
    @Test
    void getComplianceSummary_patientsAreMatchedEventCohortMinusDeviators() {
        when(inbound.countDistinctPatientsWithMatchedEvents(any(), any(), any())).thenReturn(16L);
        when(deviation.countDistinctNonCompliantAmongMatched(any(), any(), any())).thenReturn(4L);
        when(dailyKpi.getFacilityActivitySummary()).thenReturn(activity(20L, 3L, 17L, 15.0));
        when(practitioners.getRankings(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of());

        DashboardComplianceSummaryDto.PatientComplianceDto p =
                service.getComplianceSummary(null, null, null).getPatients();

        assertThat(p.getTrackedPatients()).isEqualTo(16);
        assertThat(p.getNonCompliantPatients()).isEqualTo(4);
        assertThat(p.getCompliantPatients()).isEqualTo(12);        // 16 - 4
        assertThat(p.getComplianceRate()).isEqualTo(75.0);         // 12 / 16
    }

    /** Non-compliant can never exceed tracked → compliant floors at 0, rate at 0 (no negative split). */
    @Test
    void getComplianceSummary_neverGoesNegativeWhenDeviatorsExceedTracked() {
        when(inbound.countDistinctPatientsWithMatchedEvents(any(), any(), any())).thenReturn(0L);
        when(deviation.countDistinctNonCompliantAmongMatched(any(), any(), any())).thenReturn(3L);
        when(dailyKpi.getFacilityActivitySummary()).thenReturn(activity(20L, 0L, 20L, 0.0));
        when(practitioners.getRankings(any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of());

        DashboardComplianceSummaryDto.PatientComplianceDto p =
                service.getComplianceSummary(null, null, null).getPatients();

        assertThat(p.getTrackedPatients()).isZero();
        assertThat(p.getCompliantPatients()).isZero();            // max(0, 0 - 3)
        assertThat(p.getComplianceRate()).isEqualTo(0.0);         // no divide-by-zero
    }

    @Test
    void getReferralsKpi_scopesByFacilityWhenFacilityIdGiven() {
        when(inbound.countReferralsReceivedByHIEGroupedByFacility(any(), any()))
                .thenReturn(List.of(grp("F-A", 5, 3), grp("F-B", 4, 4)));
        // Totals are scoped to the facility by the repository call.
        when(inbound.countReferralsReceivedByHIE(eq("F-A"), any(), any())).thenReturn(5L);
        when(inbound.countReferralsMatched(eq("F-A"), any(), any())).thenReturn(3L);
        when(dailyKpi.getFacilityReference()).thenReturn(List.of(
                ref("F-A", "Alpha", "North"),
                ref("F-B", "Bravo", "South")));

        ReferralsKpiDto dto = service.getReferralsKpi("F-A", null, null);

        assertThat(dto.getTotalReferralsReceived()).isEqualTo(5);
        assertThat(dto.getByFacility()).extracting(FacilityReferralCountDto::getFacilityId).containsExactly("F-A");
    }
}
