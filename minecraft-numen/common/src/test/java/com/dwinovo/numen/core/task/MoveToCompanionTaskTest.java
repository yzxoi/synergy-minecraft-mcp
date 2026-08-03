package com.dwinovo.numen.core.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression pins for the task-level arrival gate around PlayerNav. */
class MoveToCompanionTaskTest {

    @Test
    void engineArrivalAloneCannotFinalizeTask() {
        assertFalse(MoveToCompanionTask.acceptsArrival(true, false),
                "an unsupported feet/path-start cell must not be terminal success");
    }

    @Test
    void engineArrivalWithStableTaskArrivalFinalizes() {
        assertTrue(MoveToCompanionTask.acceptsArrival(true, true));
        assertFalse(MoveToCompanionTask.acceptsArrival(false, true));
    }

    @Test
    void yLevelArrivalIsStrict() {
        assertTrue(MoveToCompanionTask.atYLevel(64, 64));
        assertFalse(MoveToCompanionTask.atYLevel(63, 64),
                "one-block tolerance can claim an elevation that was not reached");
    }
}
