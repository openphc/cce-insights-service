package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.openphc.cce.insights.domain.enums.DeviationType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "deviation")
@Immutable
@Getter
public class Deviation {

    @Id
    private UUID id;

    @Column(name = "protocol_instance_id")
    private UUID protocolInstanceId;

    @Column(name = "step_instance_id")
    private UUID stepInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deviation_type")
    private DeviationType deviationType;

    @Column(name = "detected_at")
    private OffsetDateTime detectedAt;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_instance_id", insertable = false, updatable = false)
    private ProtocolInstance protocolInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_instance_id", insertable = false, updatable = false)
    private StepInstance stepInstance;

    protected Deviation() {
    }
}
