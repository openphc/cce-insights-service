package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DeviationDto {
    private UUID deviationId;
    private String patientId;
    private UUID protocolInstanceId;
    private String protocolCanonical;
    private UUID stepInstanceId;
    private String actionId;
    private String deviationType;
    private OffsetDateTime detectedAt;
    private String facilityId;
}
