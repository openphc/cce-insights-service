package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.repository.DeviationRepository;
import org.openphc.cce.insights.web.dto.DeviationByFacilityDto;
import org.openphc.cce.insights.web.dto.DeviationsByFacilityPage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeviationAnalyticsService#getDeviationsByFacility} — RI-34's
 * "Deviations by Facility and Type" chart. Repository is mocked so no ClickHouse is needed;
 * these lock the row-to-DTO mapping and pagination total pass-through.
 */
class DeviationAnalyticsServiceTest {

    private final DeviationRepository deviationRepository = mock(DeviationRepository.class);
    private final DeviationAnalyticsService service = new DeviationAnalyticsService(deviationRepository);

    /** findDeviationsByFacilityAndType row shape: [facilityId, overdue, missed, orderViolation, total]. */
    private static Object[] row(String facilityId, long overdue, long missed, long orderViolation, long total) {
        return new Object[]{facilityId, overdue, missed, orderViolation, total};
    }

    @Test
    void getDeviationsByFacility_mapsRowsAndTotalCount() {
        when(deviationRepository.findDeviationsByFacilityAndType(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        row("FAC-001", 13, 18, 11, 42),
                        row("FAC-002", 13, 14, 9, 36)));
        when(deviationRepository.countFacilitiesWithDeviations(any(), any(), any(), any(), any()))
                .thenReturn(10L);

        DeviationsByFacilityPage page = service.getDeviationsByFacility(null, null, null, null, null, "total_deviations", 20, 0);

        assertThat(page.totalCount()).isEqualTo(10L);
        assertThat(page.facilities()).hasSize(2);

        DeviationByFacilityDto first = page.facilities().get(0);
        assertThat(first.getFacilityId()).isEqualTo("FAC-001");
        assertThat(first.getOverdueCount()).isEqualTo(13);
        assertThat(first.getMissedCount()).isEqualTo(18);
        assertThat(first.getOrderViolationCount()).isEqualTo(11);
        assertThat(first.getTotalDeviations()).isEqualTo(42);

        // Structural invariant the chart relies on: overdue + missed + orderViolation == total.
        for (DeviationByFacilityDto dto : page.facilities()) {
            assertThat(dto.getOverdueCount() + dto.getMissedCount() + dto.getOrderViolationCount())
                    .isEqualTo(dto.getTotalDeviations());
        }
    }

    @Test
    void getDeviationsByFacility_emptyResult_returnsEmptyPageNotNull() {
        when(deviationRepository.findDeviationsByFacilityAndType(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(deviationRepository.countFacilitiesWithDeviations(any(), any(), any(), any(), any()))
                .thenReturn(0L);

        DeviationsByFacilityPage page = service.getDeviationsByFacility("FAC-999", null, null, null, null, "total_deviations", 20, 0);

        assertThat(page.facilities()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }
}
