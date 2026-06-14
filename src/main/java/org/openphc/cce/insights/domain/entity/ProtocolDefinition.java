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
public class ProtocolDefinition {

    private UUID id;
    private String url;
    private String version;
    private String status;
    private String definition;
    private OffsetDateTime loadedAt;
}
