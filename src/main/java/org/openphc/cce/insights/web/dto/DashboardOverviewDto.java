package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardOverviewDto {
    private long totalPatientsEBuzima;
    private long patientsReceivedHIE;
    private double transmissionRate;
    private long activeFacilities;
    private long activeDeviations;
    private long newDeviations24h;
    private long hieEventCount;
    private List<FacilityRankingDto> topFacilities;
    private List<FacilityRankingDto> bottomFacilities;
}
