package org.openphc.cce.insights.domain.enums;

/**
 * step_instances.step_status — did the expected event arrive? Written by the Matcher Service.
 * (2.0.0 split the 1.x {@code state} column into this and {@link SlaStatus}.)
 */
public enum StepStatus {
    NOT_STARTED,
    COMPLETED
}
