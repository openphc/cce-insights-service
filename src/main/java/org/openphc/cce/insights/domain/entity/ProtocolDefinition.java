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
@Table(name = "protocol_definition")
@Immutable
@Getter
public class ProtocolDefinition {

    @Id
    private UUID id;

    @Column(name = "url")
    private String url;

    @Column(name = "version")
    private String version;

    @Column(name = "status")
    private String status;

    @Column(name = "definition", columnDefinition = "jsonb")
    private String definition;

    @Column(name = "loaded_at")
    private OffsetDateTime loadedAt;

    protected ProtocolDefinition() {
    }
}
