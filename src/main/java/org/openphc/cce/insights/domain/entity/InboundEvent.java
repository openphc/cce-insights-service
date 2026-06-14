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
public class InboundEvent {

    private UUID id;
    private String cloudeventsId;
    private String source;
    private String type;
    /** Not available as a separate column in ClickHouse — extractable from raw_payload if needed. */
    private String specVersion;
    private String subject;
    private OffsetDateTime eventTime;
    /** Not available as a separate column in ClickHouse. */
    private String dataContentType;
    private String facilityId;
    private String correlationId;
    /** Not available as a separate column in ClickHouse. */
    private String sourceEventId;
    private String rawPayload;
    private String status;
    private String rejectionReason;
    private String errorDetails;
    private OffsetDateTime receivedAt;
}
