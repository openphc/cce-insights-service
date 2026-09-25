package org.openphc.cce.insights.domain.entity;

import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.domain.enums.SlaStatus;
import org.openphc.cce.insights.domain.enums.StepStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** displayStatus() folds the 2.0.0 step_status × sla_status pair into the one badge the patient views show. */
class StepInstanceTest {

    private static StepInstance step(StepStatus stepStatus, SlaStatus slaStatus) {
        return StepInstance.builder().stepStatus(stepStatus).slaStatus(slaStatus).build();
    }

    @Test
    void completedWinsOverAnySlaVerdict() {
        assertThat(step(StepStatus.COMPLETED, SlaStatus.MET).displayStatus()).isEqualTo("COMPLETED");
        assertThat(step(StepStatus.COMPLETED, SlaStatus.OVERDUE).displayStatus()).isEqualTo("COMPLETED");
        assertThat(step(StepStatus.COMPLETED, null).displayStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void outstandingStepShowsItsSlaBreach() {
        assertThat(step(StepStatus.NOT_STARTED, SlaStatus.OVERDUE).displayStatus()).isEqualTo("OVERDUE");
        assertThat(step(StepStatus.NOT_STARTED, SlaStatus.MISSED).displayStatus()).isEqualTo("MISSED");
    }

    @Test
    void unjudgedOutstandingStepIsNotStarted() {
        // 1.x PENDING and DUE both land here
        assertThat(step(StepStatus.NOT_STARTED, null).displayStatus()).isEqualTo("NOT_STARTED");
    }
}
