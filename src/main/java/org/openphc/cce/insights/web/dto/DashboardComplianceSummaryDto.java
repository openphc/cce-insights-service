package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardComplianceSummaryDto {

    private PatientComplianceDto patients;
    private FacilityComplianceDto facilities;
    private PractitionerComplianceDto practitioners;
    private ConsentComplianceDto consent;

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
        private long above90;
        private long between75And90;
        private long below75;
    }

    @Data
    @Builder
    public static class PractitionerComplianceDto {
        private long trackedPractitioners;
        private long above90;
        private long between75And90;
        private long below75;
    }

    @Data
    @Builder
    public static class ConsentComplianceDto {
        private long totalReceived;
        private long totalVerified;
        private double verificationRate;
    }
}
