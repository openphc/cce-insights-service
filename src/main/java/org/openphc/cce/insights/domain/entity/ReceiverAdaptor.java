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
public class ReceiverAdaptor {

    private UUID id;
    private String name;
    private String definition;
    private String config;
    private String endpointUrl;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
