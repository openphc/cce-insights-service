package org.openphc.cce.insights.domain.enums;

/**
 * Computed compliance category — not persisted in DB.
 * Derived from step_instance states across protocol enrollments.
 */
public enum ComplianceCategory {
    ON_TRACK,
    AT_RISK,
    NON_COMPLIANT
}
