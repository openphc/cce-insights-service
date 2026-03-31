package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class EnrollmentTrendDto {
    private UUID protocolDefinitionId;
    private String interval;
    private List<TrendPoint> trends;

    @Data
    @Builder
    public static class TrendPoint {
        private String period;
        private long enrollments;
    }
}
