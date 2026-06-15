package org.openphc.cce.insights.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openphc.cce.insights.domain.enums.DeviationType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deviation {

    private UUID id;
    private UUID protocolInstanceId;
    private UUID stepInstanceId;
    private DeviationType deviationType;
    private OffsetDateTime detectedAt;
    private String metadata;
    private UUID intelligenceEventId;
}
