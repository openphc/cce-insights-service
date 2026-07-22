package org.openphc.cce.insights.service;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FacilityDirectory resolves the global District filter to its facility set (from the facility
 * reference: [facilityId, name, expected, district]). Blank/null district = "no scoping" → null,
 * which every district-aware endpoint treats as "all facilities".
 */
class FacilityDirectoryTest {

    private final DailyKpiRepository dailyKpi = mock(DailyKpiRepository.class);
    private final FacilityDirectory directory = new FacilityDirectory(dailyKpi);

    private void stubReference() {
        when(dailyKpi.getFacilityReference()).thenReturn(List.of(
                new Object[]{"F-A", "Alpha HC",   10L, "Gasabo"},
                new Object[]{"F-B", "Bravo HC",     5L, "Gasabo"},
                new Object[]{"F-C", "Charlie HC",   0L, "Kicukiro"},
                new Object[]{"F-D", "Delta HC",     0L, ""}   // no district
        ));
    }

    @Test
    void districts_areDistinctNonBlankAndSortedCaseInsensitively() {
        stubReference();
        assertThat(directory.districts()).containsExactly("Gasabo", "Kicukiro");
    }

    @Test
    void facilityIdsInDistrict_returnsThatDistrictsFacilities_caseInsensitive() {
        stubReference();
        assertThat(directory.facilityIdsInDistrict("gasabo")).containsExactlyInAnyOrder("F-A", "F-B");
    }

    @Test
    void facilityIdsInDistrict_unknownDistrict_isEmptyNotNull() {
        stubReference();
        assertThat(directory.facilityIdsInDistrict("Nowhere")).isEmpty();
    }

    @Test
    void facilityIdsInDistrict_blankOrNull_returnsNull_withoutHittingTheReference() {
        assertThat(directory.facilityIdsInDistrict(null)).isNull();
        assertThat(directory.facilityIdsInDistrict("   ")).isNull();
        verifyNoInteractions(dailyKpi);
    }
}
