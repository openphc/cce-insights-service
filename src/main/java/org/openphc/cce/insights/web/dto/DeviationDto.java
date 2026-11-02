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
    private OffsetDateTime occurredAt;   // clinical occurrence date (when the deviation happened)
    private OffsetDateTime detectedAt;   // system detection date (when we flagged it)
    private String facilityId;
}
