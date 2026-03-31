package org.openphc.cce.insights.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceDataQualityDto {
    private List<SourceQuality> sources;

    @Data
    @Builder
    public static class SourceQuality {
        private String source;
        private long totalEvents;
        private long accepted;
        private long rejected;
        private long duplicate;
        private double acceptanceRate;
        private double rejectionRate;
        private double duplicateRate;
    }
}
