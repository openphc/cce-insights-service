package org.openphc.cce.insights.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The hand-offs the dashboard tracks: the protocol steps that pass a patient from one team or facility to the next.
 * Each is a mandatory step with a deadline (relatedAction offset), so the engine judges it on time (MET) or late
 * (OVERDUE / MISSED). {@code fromActionId} is the step the hand-off starts from, for the time it took.
 */
@ConfigurationProperties(prefix = "cce.insights.handoffs")
public record HandoffProperties(List<Handoff> items) {

    public HandoffProperties {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * @param key          stable id; the UI names the card by it, like its other dashboard cards
     * @param actionId     the step that completes the hand-off (the one with the deadline)
     * @param fromActionId the step it starts from (optional: no elapsed time without it)
     */
    public record Handoff(String key, String actionId, String fromActionId) {
    }
}
