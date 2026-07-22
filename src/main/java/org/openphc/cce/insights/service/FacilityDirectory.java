package org.openphc.cce.insights.service;

import lombok.RequiredArgsConstructor;
import org.openphc.cce.insights.domain.repository.DailyKpiRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the global District filter to the set of facilities it covers, from the facility
 * reference table (facility_id, name, expected, district_name). Used by every district-aware
 * endpoint so a district behaves as "the union of its facilities".
 */
@Service
@RequiredArgsConstructor
public class FacilityDirectory {

    private final DailyKpiRepository dailyKpiRepository;

    /** Distinct, non-empty district names, sorted case-insensitively. */
    public List<String> districts() {
        Set<String> set = new LinkedHashSet<>();
        for (Object[] row : dailyKpiRepository.getFacilityReference()) {
            String district = row.length > 3 ? (String) row[3] : null;
            if (district != null && !district.isBlank()) set.add(district);
        }
        List<String> list = new ArrayList<>(set);
        list.sort((a, b) -> a.compareToIgnoreCase(b));
        return list;
    }

    /**
     * Facility IDs in the given district. Returns null when no district is selected (blank/null),
     * signalling "no district scoping". Returns an empty list when the district matches nothing —
     * callers should treat that as "no facilities" (empty result), not "all facilities".
     */
    public List<String> facilityIdsInDistrict(String district) {
        if (district == null || district.isBlank()) return null;
        List<String> ids = new ArrayList<>();
        for (Object[] row : dailyKpiRepository.getFacilityReference()) {
            String d = row.length > 3 ? (String) row[3] : null;
            if (d != null && d.equalsIgnoreCase(district)) ids.add((String) row[0]);
        }
        return ids;
    }
}
