package org.openphc.cce.insights.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolInstance {

    private UUID id;
    private UUID protocolDefinitionId;
    private String patientId;
    private String protocolCanonical;
    private ProtocolInstanceStatus status;
    private OffsetDateTime enrolledAt;
    /** Mapped from enrolled_at in ClickHouse (no separate created_at column). */
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
