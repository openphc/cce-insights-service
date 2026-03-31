package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_log")
@Immutable
@Getter
public class EventLog {

    @Id
    private UUID id;

    @Column(name = "cloudevents_id")
    private String cloudeventsId;

    @Column(name = "subject")
    private String subject;

    @Column(name = "type")
    private String type;

    @Column(name = "event_time")
    private OffsetDateTime eventTime;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "source")
    private String source;

    @Column(name = "data", columnDefinition = "jsonb")
    private String data;

    @Column(name = "processing_status")
    private String processingStatus;

    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "protocol_instance_id")
    private UUID protocolInstanceId;

    @Column(name = "protocol_definition_id")
    private UUID protocolDefinitionId;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "matched_step_instance_id")
    private UUID matchedStepInstanceId;

    protected EventLog() {
    }
}
