package org.openphc.cce.insights.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What kind of thing a PlanDefinition action is, read the same way the Protocol and Matcher
 * services read it (cce-common-util PlanDefinitionParser).
 */
public final class PlanDefinitionActions {

    private PlanDefinitionActions() {}

    /**
     * A step group: an action with {@code groupingBehavior: logical-group}. It is a set of steps
     * that belong together, such as one visit. It never matches an event, so it has no step
     * instances of its own. Its occurrences are rows of step_group_instances.
     */
    public static boolean isStepGroup(JsonNode action) {
        return "logical-group".equals(action.path("groupingBehavior").asText(null));
    }

    /** An intelligence action (notification, escalation). Not a step of the patient's journey. */
    public static boolean isFireEvent(JsonNode action) {
        JsonNode coding = action.path("type").path("coding");
        if (!coding.isArray()) return false;
        for (JsonNode c : coding) {
            if ("fire-event".equals(c.path("code").asText(null))) return true;
        }
        return false;
    }
}
