package com.dwinovo.numen.core.task;

import com.dwinovo.numen.task.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract tests for the FailureType → ErrorCode/next_steps mapping. */
class FailureGuidanceTest {

    @Test
    void everyFailureTypeHasGuidance() {
        for (FailureType t : FailureType.values()) {
            FailureGuidance.Guidance g = FailureGuidance.forType(t);
            assertNotNull(g, "missing guidance for " + t);
            assertNotNull(g.errorCode());
            assertFalse(g.nextSteps().isEmpty(), "empty next_steps for " + t);
            assertFalse(g.nextSteps().get(0).isBlank());
        }
    }

    @Test
    void inLadderFailuresMapToWorldStateAndAreRetryable() {
        for (FailureType t : new FailureType[]{FailureType.OCCLUDED, FailureType.BOXED_IN,
                FailureType.NO_PATH, FailureType.NO_SUPPORT, FailureType.OUT_OF_REACH,
                FailureType.STANCE_DUD, FailureType.HAZARD}) {
            FailureGuidance.Guidance g = FailureGuidance.forType(t);
            assertEquals(ErrorCode.WORLD_STATE, g.errorCode());
            assertTrue(g.retryable(), t + " should be retryable");
        }
    }

    @Test
    void kickBackFailuresMapToNotFoundOrWorldStateAndAreNotRetryable() {
        FailureGuidance.Guidance targetLost = FailureGuidance.forType(FailureType.TARGET_LOST);
        assertEquals(ErrorCode.NOT_FOUND, targetLost.errorCode());
        assertFalse(targetLost.retryable());

        FailureGuidance.Guidance minedOut = FailureGuidance.forType(FailureType.MINED_OUT);
        assertEquals(ErrorCode.NOT_FOUND, minedOut.errorCode());
        assertFalse(minedOut.retryable());

        FailureGuidance.Guidance noMaterial = FailureGuidance.forType(FailureType.NO_MATERIAL);
        assertEquals(ErrorCode.WORLD_STATE, noMaterial.errorCode());
        assertFalse(noMaterial.retryable());
    }

    @Test
    void lifecycleAndImplementationFaultsMapToTheirOwnDomains() {
        assertEquals(ErrorCode.CANCELLED, FailureGuidance.forType(FailureType.INTERRUPTED).errorCode());
        assertEquals(ErrorCode.TIMEOUT, FailureGuidance.forType(FailureType.TIMED_OUT).errorCode());
        assertTrue(FailureGuidance.forType(FailureType.TIMED_OUT).retryable());
        assertEquals(ErrorCode.UNSUPPORTED, FailureGuidance.forType(FailureType.UNSUPPORTED).errorCode());
        assertEquals(ErrorCode.INTERNAL, FailureGuidance.forType(FailureType.INTERNAL).errorCode());
        assertFalse(FailureGuidance.forType(FailureType.INTERNAL).retryable());
    }

    @Test
    void errorBlockCarriesCodeRetryableNextStepsAndDetails() {
        Map<String, Object> block = FailureGuidance.errorBlock(FailureType.NO_PATH, "no route to target");
        assertEquals("world_state", block.get("code"));
        assertEquals(true, block.get("retryable"));
        assertTrue(block.get("next_steps") instanceof java.util.List<?>);
        assertTrue(((java.util.List<?>) block.get("next_steps")).size() >= 1);
        assertEquals("no route to target", block.get("details"));
    }
}
