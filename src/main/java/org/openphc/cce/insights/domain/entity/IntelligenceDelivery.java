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
public class IntelligenceDelivery {

    private UUID id;
    private UUID intelligenceEventId;
    private UUID actionDefinitionId;
    private UUID destinationAdaptorMappingId;
    private String actionType;
    private String status;
    private String subject;
    private String protocolCanonical;
    private String actionId;
    private String severity;
    private String destination;
    private int attemptCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deliveredAt;
}
