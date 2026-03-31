package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class RepeatDeviationPatientDto {
    private String patientId;
    private long totalDeviations;
    private long overdueCount;
    private long missedCount;
    private long affectedProtocols;
    private long affectedSteps;
    private String facilityId;
    private List<DeviationDetail> deviations;

    @Data
    @Builder
    public static class DeviationDetail {
        private String protocolCanonical;
        private String actionId;
        private String deviationType;
        private OffsetDateTime detectedAt;
    }
}
