package org.openphc.cce.insights.domain.enums;

/**
 * step_instances.sla_status — was the deadline met? Written by the Step SLA Service.
 * The column is '' until a verdict is reached (NULL in PostgreSQL), which maps to a null
 * SlaStatus here; optional steps stay unjudged for good. MET is only reached by a completion
 * that beat the due date, so OVERDUE/MISSED can sit on either a completed (late) or an
 * outstanding step — read it together with {@link StepStatus}.
 */
public enum SlaStatus {
    OVERDUE,
    MISSED,
    MET
}
