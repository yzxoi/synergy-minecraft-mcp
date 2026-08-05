package com.dwinovo.numen.task;

/**
 * Machine-readable error domain for tool results. Distinct from
 * {@link TaskState} (the task lifecycle) and {@code FailureType} (the core's
 * structured "why did it fail" diagnosis): this is the <em>external contract</em>
 * an LLM/MCP caller can branch on without parsing prose, plus the default
 * human-readable message and retry policy.
 *
 * <p>The domain deliberately separates <b>transport/network errors</b> (the
 * request never reached the world or the answer never came back — the game state
 * is untouched) from <b>world-state errors</b> (the world said no). A model that
 * conflates the two retries forever on a permanent refusal, or gives up on a
 * transient blip.
 *
 * <p>Serialised into {@link TaskResult} as the {@code code} field; {@link #retryable}
 * maps to the {@code retryable} field; guidance lives in {@code next_steps} (see
 * the {@code FailureGuidance} mapping in core).
 */
public enum ErrorCode {

    /** Arguments or tool state are invalid for this call (bad id, missing field, illegal combo). */
    VALIDATION("validation", "The arguments or current state do not permit this call.", false),
    /** The referenced companion / task / item / block does not exist (or expired). */
    NOT_FOUND("not_found", "The referenced object does not exist.", false),
    /** The body is already occupied by another task. */
    BUSY("busy", "The body is busy with another task.", false),
    /** The world refused the action (unreachable, hazard, wrong dimension, no material). */
    WORLD_STATE("world_state", "The world does not permit this action right now.", false),
    /** Transport failure (timeout, disconnect, oversized body) — the game state was NOT changed. */
    NETWORK("network", "A transport error occurred; the game state was not changed.", true),
    /** The operation did not finish within its time budget. */
    TIMEOUT("timeout", "The operation did not finish within the allowed time.", true),
    /** The action is not supported in the current context (record type, mode, tool). */
    UNSUPPORTED("unsupported", "This action is not supported here.", false),
    /** Cancelled (owner stop, death, preemption). */
    CANCELLED("cancelled", "The operation was cancelled.", false),
    /** The implementation hit an unexpected error — a bug worth reporting, not retrying. */
    INTERNAL("internal", "An internal error occurred; this is a bug worth reporting.", false);

    private final String code;
    private final String defaultMessage;
    private final boolean retryable;

    ErrorCode(String code, String defaultMessage, boolean retryable) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.retryable = retryable;
    }

    /** Stable wire value (snake_case, used in {@code TaskResult.code}). */
    public String code() {
        return code;
    }

    /** Default human-readable message when a tool does not supply a more specific one. */
    public String defaultMessage() {
        return defaultMessage;
    }

    /** Whether a caller is <em>allowed</em> to retry the same call as-is. */
    public boolean retryable() {
        return retryable;
    }

    /** Resolve a wire code back to an enum value, defaulting to {@link #WORLD_STATE} for unknowns. */
    public static ErrorCode fromCode(String wire) {
        if (wire == null) return WORLD_STATE;
        for (ErrorCode e : values()) {
            if (e.code.equals(wire)) return e;
        }
        return WORLD_STATE;
    }
}
