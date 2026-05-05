package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "intelligence_delivery")
@Immutable
@Getter
public class IntelligenceDelivery {

    @Id
    private UUID id;

    @Column(name = "intelligence_event_id")
    private UUID intelligenceEventId;

    @Column(name = "action_definition_id")
    private UUID actionDefinitionId;

    @Column(name = "destination_adaptor_mapping_id")
    private UUID destinationAdaptorMappingId;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "status")
    private String status;

    @Column(name = "subject")
    private String subject;

    @Column(name = "protocol_canonical")
    private String protocolCanonical;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "severity")
    private String severity;

    @Column(name = "destination")
    private String destination;

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;
}
