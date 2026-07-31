package org.openphc.cce.insights.web.dto;

import java.util.List;

public record DeviationsByFacilityPage(List<DeviationByFacilityDto> facilities, long totalCount) {}
