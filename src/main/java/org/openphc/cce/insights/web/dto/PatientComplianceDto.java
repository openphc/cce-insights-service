package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class PatientComplianceDto {
    private String patientId;
    private String protocolInstanceId;
    private String protocolCanonical;
    private OffsetDateTime enrolledAt;
    private String status;
    private double complianceRate;
    private String complianceCategory;
    private long stepsCompleted;
    private long totalSteps;
    private long activeDeviations;
    private String facilityId;
}
