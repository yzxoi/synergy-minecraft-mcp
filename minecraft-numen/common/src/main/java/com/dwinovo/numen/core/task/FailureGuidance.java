package com.dwinovo.numen.core.task;

import com.dwinovo.numen.task.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a {@link FailureType} to the external observability contract: an
 * {@link ErrorCode} domain plus model-facing {@code next_steps}. Lives in core
 * (not numen-api) because it maps core's own diagnosis enum — the dependency
 * direction is core → api ({@code FailureType} is core's, {@link ErrorCode} is
 * api's).
 *
 * <p>The {@code next_steps} strings are deliberately actionable and reference
 * concrete tools, so an LLM receiving a failed tool result can pick the next
 * call without guessing. In-ladder failures (a different way to reach the SAME
 * bounded goal) suggest replanning/repositioning; kick-back-to-LLM failures
 * (a prerequisite gap) suggest acquiring the missing thing, widening the
 * search, or stopping.
 */
public final class FailureGuidance {

    private static final Map<FailureType, Guidance> TABLE = buildTable();

    /** Structured guidance for one failure type. */
    public record Guidance(ErrorCode errorCode, boolean retryable, List<String> nextSteps) {}

    private FailureGuidance() {}

    /** The mapped guidance for a failure type; never null (unknowns fall back to world_state). */
    public static Guidance forType(FailureType type) {
        Guidance g = TABLE.get(type);
        return g != null ? g : new Guidance(ErrorCode.WORLD_STATE, false,
                List.of("Inspect the situation with get_self_status / scan_blocks, then choose a different approach."));
    }

    /**
     * Structured error block for the TaskResult envelope:
     * {@code {code, retryable, next_steps[]}} — the machine-readable part of a
     * failed result. {@code details} carries the original failure reason.
     */
    public static Map<String, Object> errorBlock(FailureType type, String details) {
        Guidance g = forType(type);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", g.errorCode().code());
        out.put("retryable", g.retryable());
        out.put("next_steps", g.nextSteps());
        if (details != null && !details.isBlank()) {
            out.put("details", details);
        }
        return out;
    }

    private static Map<FailureType, Guidance> buildTable() {
        Map<FailureType, Guidance> table = new LinkedHashMap<>();

        // ---- in-ladder: a different execution of the SAME bounded goal ----
        table.put(FailureType.OCCLUDED, new Guidance(ErrorCode.WORLD_STATE, true,
                List.of("Reposition for a clear line of sight (goto to another side), then retry the action.")));
        table.put(FailureType.BOXED_IN, new Guidance(ErrorCode.WORLD_STATE, true,
                List.of("Stop and reassess: run look_around / scan_blocks to find the blockage, then pick a different route or dig out.")));
        table.put(FailureType.NO_PATH, new Guidance(ErrorCode.WORLD_STATE, true,
                List.of("Try a looser goal (near/adjacent) or a different destination; verify reachability with look_around before retrying.")));
        table.put(FailureType.NO_SUPPORT, new Guidance(ErrorCode.WORLD_STATE, true,
                List.of("Place a support block (cobblestone/dirt) at the target first, then retry.")));
        table.put(FailureType.OUT_OF_REACH, new Guidance(ErrorCode.WORLD_STATE, true,
                List.of("Move closer (goto to the target's block) so the action is within reach, then retry.")));
        table.put(FailureType.STANCE_DUD, new Guidance(ErrorCode.WORLD_STATE, true,
                List.of("Reposition to a stance that can actually do the work (on the ground, facing the target), then retry.")));
        table.put(FailureType.HAZARD, new Guidance(ErrorCode.WORLD_STATE, true,
                List.of("Route around the hazard (lava/water/void); if it cannot be avoided, stop and choose another target.")));

        // ---- kick-back-to-LLM: a prerequisite gap the deterministic layer must not decide ----
        table.put(FailureType.NO_MATERIAL, new Guidance(ErrorCode.WORLD_STATE, false,
                List.of("Acquire the missing material (mine the source block, craft it, or collect it), then retry.")));
        table.put(FailureType.WRONG_TOOL, new Guidance(ErrorCode.WORLD_STATE, false,
                List.of("Equip the correct tool for the job (equip_item), then retry.")));
        table.put(FailureType.TARGET_LOST, new Guidance(ErrorCode.NOT_FOUND, false,
                List.of("Rescan with scan_nearby_entities / scan_blocks to find the current target, or pick a new one.")));
        table.put(FailureType.MINED_OUT, new Guidance(ErrorCode.NOT_FOUND, false,
                List.of("Widen the search radius or scan a different area; if none remains, stop and choose another plan.")));
        table.put(FailureType.ENTITY_BLOCKED, new Guidance(ErrorCode.WORLD_STATE, false,
                List.of("Wait for the entity to move, lure it away, or pick another target cell.")));
        table.put(FailureType.LOW_HEALTH, new Guidance(ErrorCode.LOW_HEALTH, true,
                List.of("Recover health above the combat flee_health threshold, verify nearby threats, then issue a fresh combat task if still needed.")));

        // ---- lifecycle terminal states ----
        table.put(FailureType.INTERRUPTED, new Guidance(ErrorCode.CANCELLED, false,
                List.of("The task was cancelled. Verify the world state with perception, then issue a fresh task if still desired.")));
        table.put(FailureType.TIMED_OUT, new Guidance(ErrorCode.TIMEOUT, true,
                List.of("The task exceeded its time budget. Check task_status for progress made, then retry with a smaller scope or verify the goal is still reachable.")));

        // ---- implementation faults ----
        table.put(FailureType.UNSUPPORTED, new Guidance(ErrorCode.UNSUPPORTED, false,
                List.of("This action is not supported in the current context; try a different tool.")));
        table.put(FailureType.INTERNAL, new Guidance(ErrorCode.INTERNAL, false,
                List.of("An internal error occurred — do not blindly retry. Report the task result to the mod author.")));
        table.put(FailureType.UNKNOWN, new Guidance(ErrorCode.WORLD_STATE, false,
                List.of("Inspect the situation with get_self_status / scan_blocks, then choose a different approach.")));

        return table;
    }
}
