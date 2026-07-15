package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.web.dto.AdoptionKpiDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdoptionService} — the e-Buzima adoption math layered on
 * mv_daily_adoption_kpis. Locks the invariants that keep the three table columns consistent:
 * actualVisitsPerDay is a ceiling of the daily average, reportingGap is derived from that same
 * rounded value, silent days still count toward the calendar-day denominator, and facilities
 * absent from the MV are filled from the reference list. Repository is mocked (no ClickHouse).
 */
class AdoptionServiceTest {

    private final DailyKpiRepository repo = mock(DailyKpiRepository.class);
    private final AdoptionService service = new AdoptionService(repo);

    /** adoption row: [facility_id, expected_per_day, sum_actual, adoption_rate_pct]. */
    private static Object[] adoption(String id, long expected, double sumActual, double ratePct) {
        return new Object[]{id, expected, sumActual, ratePct};
    }

    /** facility reference row: [facility_id, facility_name, expected_per_day]. */
    private static Object[] ref(String id, String name, long expected) {
        return new Object[]{id, name, expected};
    }

    private static Map<String, AdoptionKpiDto> byId(List<AdoptionKpiDto> rows) {
        return rows.stream().collect(Collectors.toMap(AdoptionKpiDto::getFacilityId, r -> r));
    }

    @Test
    void getAdoptionKpis_ceilsActualAndDerivesGapForToday() {
        // calendarDays = 1 for today's snapshot: actual = ceil(7) = 7, gap = 10 - 7 = 3.
        when(repo.getAdoptionKpis()).thenReturn(List.<Object[]>of(adoption("F-A", 10, 7.0, 70.0)));
        when(repo.getFacilityReference()).thenReturn(List.<Object[]>of(ref("F-A", "Alpha", 10)));

        AdoptionKpiDto dto = service.getAdoptionKpis().get(0);

        assertThat(dto.getExpectedVisitsPerDay()).isEqualTo(10);
        assertThat(dto.getActualVisitsPerDay()).isEqualTo(7);
        assertThat(dto.getAdoptionRate()).isEqualTo(70.0);
        assertThat(dto.getReportingGapPerDay()).isEqualTo(3);
    }

    @Test
    void getAdoptionKpisByDateRange_averagesOverCalendarDaysIncludingSilentDays() {
        // 10 calendar days, 30 total visits → ceil(30 / 10) = 3 per day; gap = 8 - 3 = 5.
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 10);
        when(repo.getAdoptionKpisByDateRange(start, end)).thenReturn(List.<Object[]>of(adoption("F-A", 8, 30.0, 37.5)));
        when(repo.getFacilityReference()).thenReturn(List.<Object[]>of(ref("F-A", "Alpha", 8)));

        AdoptionKpiDto dto = service.getAdoptionKpisByDateRange(start, end).get(0);

        assertThat(dto.getActualVisitsPerDay()).isEqualTo(3);
        assertThat(dto.getReportingGapPerDay()).isEqualTo(5);
    }

    @Test
    void getAdoptionKpis_fillsFacilitiesMissingFromTheMvFromReferenceList() {
        // F-B never reported → filled as zero activity, rate 0, gap = full expected.
        when(repo.getAdoptionKpis()).thenReturn(List.<Object[]>of(adoption("F-A", 10, 9.0, 90.0)));
        when(repo.getFacilityReference()).thenReturn(List.<Object[]>of(ref("F-A", "Alpha", 10), ref("F-B", "Bravo", 6)));

        Map<String, AdoptionKpiDto> result = byId(service.getAdoptionKpis());

        assertThat(result).containsOnlyKeys("F-A", "F-B");
        AdoptionKpiDto b = result.get("F-B");
        assertThat(b.getActualVisitsPerDay()).isZero();
        assertThat(b.getAdoptionRate()).isEqualTo(0.0);
        assertThat(b.getReportingGapPerDay()).isEqualTo(6);
    }

    @Test
    void getAdoptionKpis_ordersByAdoptionRateDescending() {
        when(repo.getAdoptionKpis()).thenReturn(List.of(
                adoption("F-A", 10, 4.0, 40.0),
                adoption("F-B", 10, 9.0, 90.0)));
        when(repo.getFacilityReference()).thenReturn(List.<Object[]>of(ref("F-A", "Alpha", 10), ref("F-B", "Bravo", 10)));

        List<AdoptionKpiDto> result = service.getAdoptionKpis();

        assertThat(result.get(0).getFacilityId()).isEqualTo("F-B");
        assertThat(result.get(1).getFacilityId()).isEqualTo("F-A");
    }

    @Test
    void getAdoptionKpis_zeroExpectedAndZeroActualReportsZeroAdoptionAndNoGap() {
        // A facility with neither an expected baseline nor any actual visits is 0% adopted — not a
        // vacuous 100%. (No negative/percentage-of-zero artefact either; gap stays 0.)
        when(repo.getAdoptionKpis()).thenReturn(List.of());
        when(repo.getFacilityReference()).thenReturn(List.<Object[]>of(ref("F-Z", "Zulu", 0)));

        AdoptionKpiDto dto = service.getAdoptionKpis().get(0);

        assertThat(dto.getActualVisitsPerDay()).isZero();
        assertThat(dto.getAdoptionRate()).isEqualTo(0.0);
        assertThat(dto.getReportingGapPerDay()).isZero();
    }
}
