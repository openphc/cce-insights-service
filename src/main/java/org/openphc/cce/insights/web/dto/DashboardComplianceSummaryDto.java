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
        private long trackedFacilities;     // total_in_scope from facility_reference
        private long activeFacilities;      // transmitted ≥1 HIE event today
        private long inactiveFacilities;    // in scope but no events today
        private double activeFacilityRate;  // active / total_in_scope × 100
    }

    @Data
    @Builder
    public static class PractitionerComplianceDto {
        private long trackedPractitioners;
        private long above90;
        private long between75And90;
        private long below75;
    }
}
