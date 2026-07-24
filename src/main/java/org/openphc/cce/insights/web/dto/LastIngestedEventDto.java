package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class LastIngestedEventDto {
    private OffsetDateTime lastEventTime;
}
