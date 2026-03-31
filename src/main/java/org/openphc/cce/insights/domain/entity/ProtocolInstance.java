package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.openphc.cce.insights.domain.enums.ProtocolInstanceStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "protocol_instance")
@Immutable
@Getter
public class ProtocolInstance {

    @Id
    private UUID id;

    @Column(name = "protocol_definition_id")
    private UUID protocolDefinitionId;

    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "protocol_canonical")
    private String protocolCanonical;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProtocolInstanceStatus status;

    @Column(name = "enrolled_at")
    private OffsetDateTime enrolledAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_definition_id", insertable = false, updatable = false)
    private ProtocolDefinition protocolDefinition;

    protected ProtocolInstance() {
    }
}
