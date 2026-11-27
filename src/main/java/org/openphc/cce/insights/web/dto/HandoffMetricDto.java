package org.openphc.cce.insights.web.dto;

import lombok.Builder;
import lombok.Data;

/**
 * One hand-off over the dashboard's date range (by the hand-off's due time). Counts are hand-offs (step instances),
 * not patients. A deviation is a hand-off that missed its deadline: completed late, or still outstanding past it.
 */
@Data
@Builder
public class HandoffMetricDto {
    /** Stable id of the hand-off; the UI names the card by it. */
    private String key;
    private String actionId;
    /** Hand-offs with a deadline in the range: completed (on time or late), or outstanding. */
    private long total;
    private long completed;
    private long onTime;
    private long completedLate;
    /** Outstanding and already past the deadline. */
    private long overdueOpen;
    /** Outstanding, deadline not reached yet. */
    private long pending;
    /** completedLate + overdueOpen. */
    private long deviations;
    /** deviations / (total - pending), as a percentage: the share of judged hand-offs that slipped. */
    private double deviationRate;
    /** Median minutes from the step it starts from to its completion; null when none can be measured. */
    private Double medianMinutes;
}
