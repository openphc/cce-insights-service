package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardComplianceSummaryDto {

    private PatientComplianceDto patients;
    private FacilityComplianceDto facilities;
    private PractitionerComplianceDto practitioners;

    @Data
    @Builder
    public static class PatientComplianceDto {
        private long trackedPatients;
        private long compliantPatients;
        private long nonCompliantPatients;
        private double complianceRate;
    }

    @Data
    @Builder
    public static class FacilityComplianceDto {
        private long trackedFacilities;
        private long compliantFacilities;
        private long nonCompliantFacilities;
        private double complianceRate;
    }

    @Data
    @Builder
    public static class PractitionerComplianceDto {
        private long trackedPractitioners;
        private long compliantPractitioners;
        private long nonCompliantPractitioners;
        private double complianceRate;
    }
}
