package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.openphc.cce.insights.domain.repository.InboundEventRepository;
import org.openphc.cce.insights.web.dto.FacilityActivityItemDto;
import org.openphc.cce.insights.web.dto.FacilityActivitySummaryDto;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FacilityActivityService}. RI-62 dropped the requirement that an active
 * facility's event also be protocol-matched — the repository (not covered by a repository-level
 * test in this codebase, see DailyKpiRepositoryImpl) now derives active/inactive from
 * status='ACCEPTED' alone. These tests lock the service's row-mapping/DTO-building logic — the
 * one path here (getActivitySummaryForFacility) that has real branching logic of its own — and
 * guard against index-order regressions in the Object[] contract with the repository.
 */
class FacilityActivityServiceTest {

    private final DailyKpiRepository dailyKpiRepository = mock(DailyKpiRepository.class);
    private final InboundEventRepository inboundEventRepository = mock(InboundEventRepository.class);
    private final FacilityActivityService service =
            new FacilityActivityService(dailyKpiRepository, inboundEventRepository);

    /** activity summary row: [totalInScope, activeFacilities, inactiveFacilities, activeFacilityRate]. */
    private static Object[] summary(long total, long active, long inactive, double rate) {
        return new Object[]{total, active, inactive, rate};
    }

    /** activity detail row: [facilityId, facilityName, district, lastActivity, active(0/1)]. */
    private static Object[] detail(String id, String name, String district, String lastActivity, int active) {
        return new Object[]{id, name, district, lastActivity, active};
    }

    @Test
    void getActivitySummary_mapsRepositoryRowByIndex() {
        when(dailyKpiRepository.getFacilityActivitySummary()).thenReturn(summary(25, 19, 6, 76.0));

        FacilityActivitySummaryDto dto = service.getActivitySummary();

        assertThat(dto.getTotalInScope()).isEqualTo(25);
        assertThat(dto.getActiveFacilities()).isEqualTo(19);
        assertThat(dto.getInactiveFacilities()).isEqualTo(6);
        assertThat(dto.getActiveFacilityRate()).isEqualTo(76.0);
    }

    @Test
    void getActivitySummaryByDateRange_mapsRepositoryRowByIndex() {
        LocalDate start = LocalDate.of(2026, 4, 28);
        LocalDate end = LocalDate.of(2026, 7, 27);
        when(dailyKpiRepository.getFacilityActivitySummaryByDateRange(start, end))
                .thenReturn(summary(1, 1, 0, 100.0));

        FacilityActivitySummaryDto dto = service.getActivitySummaryByDateRange(start, end);

        assertThat(dto.getTotalInScope()).isEqualTo(1);
        assertThat(dto.getActiveFacilities()).isEqualTo(1);
        assertThat(dto.getInactiveFacilities()).isZero();
        assertThat(dto.getActiveFacilityRate()).isEqualTo(100.0);
    }

    @Test
    void getActivitySummaryForFacility_activeWhenFacilityTransmittedInRange() {
        // RI-62: "transmitted" here is facilityTransmittedInRange, which is accepted-only
        // (no MATCHED requirement) — a facility with an accepted-but-unmatched event still
        // counts as active, consistent with the rest of the Active/Inactive fix.
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        when(inboundEventRepository.facilityTransmittedInRange(anyString(), any(), any())).thenReturn(true);

        FacilityActivitySummaryDto dto = service.getActivitySummaryForFacility("0011", start, end);

        assertThat(dto.getTotalInScope()).isEqualTo(1);
        assertThat(dto.getActiveFacilities()).isEqualTo(1);
        assertThat(dto.getInactiveFacilities()).isZero();
        assertThat(dto.getActiveFacilityRate()).isEqualTo(100.0);
    }

    @Test
    void getActivitySummaryForFacility_inactiveWhenFacilityDidNotTransmitInRange() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        when(inboundEventRepository.facilityTransmittedInRange(anyString(), any(), any())).thenReturn(false);

        FacilityActivitySummaryDto dto = service.getActivitySummaryForFacility("0011", start, end);

        assertThat(dto.getTotalInScope()).isEqualTo(1);
        assertThat(dto.getActiveFacilities()).isZero();
        assertThat(dto.getInactiveFacilities()).isEqualTo(1);
        assertThat(dto.getActiveFacilityRate()).isEqualTo(0.0);
    }

    @Test
    void getFacilityActivityDetail_mapsActiveFlagAndSortsByDistrictThenFacilityName() {
        LocalDate start = LocalDate.of(2026, 4, 28);
        LocalDate end = LocalDate.of(2026, 7, 27);
        when(dailyKpiRepository.getFacilityActivityDetail(start, end)).thenReturn(List.of(
                detail("0022", "Zulu CS", "North", "2026-07-01", 1),
                detail("0011", "Rwanda Facility", "North", null, 0),
                detail("0001", "Alpha CS", "East", "2026-06-22", 1)));

        List<FacilityActivityItemDto> rows = service.getFacilityActivityDetail(start, end);

        // Sorted: district A→Z (East before North), then facility name A→Z within district.
        assertThat(rows).extracting(FacilityActivityItemDto::getFacilityId)
                .containsExactly("0001", "0011", "0022");

        FacilityActivityItemDto rwanda = rows.get(1);
        assertThat(rwanda.getFacilityId()).isEqualTo("0011");
        assertThat(rwanda.isActive()).isFalse();
        assertThat(rwanda.getLastActivity()).isNull();

        FacilityActivityItemDto zulu = rows.get(2);
        assertThat(zulu.isActive()).isTrue();
        assertThat(zulu.getLastActivity()).isEqualTo("2026-07-01");
    }
}
