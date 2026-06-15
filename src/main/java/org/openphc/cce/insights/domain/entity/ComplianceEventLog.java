package org.openphc.cce.insights.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceEventLog {

    private UUID id;
    private String cloudeventsId;
    private String subject;
    private String type;
    private OffsetDateTime eventTime;
    private OffsetDateTime receivedAt;
    private String source;
    private String data;
    private String processingStatus;
    private String facilityId;
    private UUID protocolInstanceId;
    private UUID protocolDefinitionId;
    private String actionId;
    private UUID matchedStepInstanceId;
}
