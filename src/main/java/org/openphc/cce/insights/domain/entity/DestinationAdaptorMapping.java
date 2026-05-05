package org.openphc.cce.insights.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "destination_adaptor_mapping")
@Immutable
@Getter
public class DestinationAdaptorMapping {

    @Id
    private UUID id;

    @Column(name = "destination")
    private String destination;

    @Column(name = "receiver_adaptor_id")
    private UUID receiverAdaptorId;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
