package org.openphc.cce.insights.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class PaginationDto {
    private int limit;
    private String nextCursor;
    private boolean hasMore;
}
