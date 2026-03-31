package org.openphc.cce.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FacilitySummaryDto {
    private String facilityId;
    private long totalPatients;
    private long totalEnrollments;
    private double overallComplianceRate;
    private List<ProtocolBreakdown> protocolBreakdown;

    @Data
    @Builder
    public static class ProtocolBreakdown {
        private String protocolDefinitionId;
        private String protocolCanonical;
        private long enrollments;
        private double complianceRate;
        private long activeDeviations;
    }
}
