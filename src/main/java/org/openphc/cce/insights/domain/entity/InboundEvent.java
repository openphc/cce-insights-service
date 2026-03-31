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
@Table(name = "inbound_event")
@Immutable
@Getter
public class InboundEvent {

    @Id
    private UUID id;

    @Column(name = "cloudevents_id")
    private String cloudeventsId;

    @Column(name = "source")
    private String source;

    @Column(name = "type")
    private String type;

    @Column(name = "spec_version")
    private String specVersion;

    @Column(name = "subject")
    private String subject;

    @Column(name = "event_time")
    private OffsetDateTime eventTime;

    @Column(name = "data_content_type")
    private String dataContentType;

    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "source_event_id")
    private String sourceEventId;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "status")
    private String status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "error_details")
    private String errorDetails;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;
}
