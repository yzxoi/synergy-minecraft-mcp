package com.dwinovo.numen.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract tests for the machine-readable error domain. */
class ErrorCodeTest {

    @Test
    void wireCodesAreStableSnakeCase() {
        assertEquals("validation", ErrorCode.VALIDATION.code());
        assertEquals("not_found", ErrorCode.NOT_FOUND.code());
        assertEquals("busy", ErrorCode.BUSY.code());
        assertEquals("world_state", ErrorCode.WORLD_STATE.code());
        assertEquals("network", ErrorCode.NETWORK.code());
        assertEquals("timeout", ErrorCode.TIMEOUT.code());
        assertEquals("unsupported", ErrorCode.UNSUPPORTED.code());
        assertEquals("cancelled", ErrorCode.CANCELLED.code());
        assertEquals("internal", ErrorCode.INTERNAL.code());
    }

    @Test
    void retryPolicySeparatesTransientFromPermanent() {
        assertTrue(ErrorCode.NETWORK.retryable());
        assertTrue(ErrorCode.TIMEOUT.retryable());
        assertFalse(ErrorCode.VALIDATION.retryable());
        assertFalse(ErrorCode.NOT_FOUND.retryable());
        assertFalse(ErrorCode.BUSY.retryable());
        assertFalse(ErrorCode.WORLD_STATE.retryable());
        assertFalse(ErrorCode.INTERNAL.retryable());
    }

    @Test
    void fromCodeResolvesKnownValuesAndDefaultsUnknownsToWorldState() {
        assertEquals(ErrorCode.NETWORK, ErrorCode.fromCode("network"));
        assertEquals(ErrorCode.TIMEOUT, ErrorCode.fromCode("timeout"));
        assertEquals(ErrorCode.WORLD_STATE, ErrorCode.fromCode("totally-unknown"));
        assertEquals(ErrorCode.WORLD_STATE, ErrorCode.fromCode(null));
    }

    @Test
    void defaultMessagesAreNonBlankEnglish() {
        for (ErrorCode e : ErrorCode.values()) {
            assertFalse(e.defaultMessage().isBlank());
        }
    }
}
