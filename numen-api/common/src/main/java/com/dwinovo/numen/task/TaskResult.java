package com.dwinovo.numen.task;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome a task hands back to the LLM agent loop. Serialised as the
 * {@code content} of a {@code role:tool} message in the next chat completion
 * request.
 *
 * <h2>Shape decision</h2>
 * Modeled after Mindcraft's {@code {success, message, timedout, interrupted}}
 * envelope (validated by their open-source agent on small models). The
 * {@code data} map carries task-specific structured info — e.g. moveTo
 * reports {@code final_x/y/z}, future scan_inventory would report
 * {@code items: [...]}. Keys are lowercase snake_case; values must be
 * Gson-serialisable.
 *
 * <p>Composite tasks (Phase-2) will use the same envelope but populate
 * {@code data.step_results} with the per-step result list, so a failed chain
 * can be traced step-by-step by the LLM.
 *
 * <h2>Observability extension (external contract)</h2>
 * The optional {@code code / retryable / next_steps / situation} fields give
 * an external (MCP) driver a machine-readable error domain plus the body's
 * current situation snapshot, without disturbing the legacy five-field shape:
 * they only appear in {@link #toData()} when non-null, so older consumers
 * (the built-in brain, client-side result harvesters) keep working byte-for-
 * byte.
 *
 * @param success      did the task achieve its goal? Distinct from
 *                     {@code !timedOut && !interrupted}: a moveTo can succeed,
 *                     fail (unreachable), time out, or get cancelled.
 * @param message      short human-readable summary. Visible in agent logs and
 *                     useful for the LLM to reason about what happened
 *                     ("path ended before reaching target").
 * @param timedOut     whether the work ran out of time.
 * @param interrupted  whether the work was cancelled (e.g. owner interrupt).
 * @param data         task-specific structured payload. Empty map for no extras.
 */
public class TaskResult {

    private static final Gson GSON = new Gson();

    private final boolean success;
    private final String message;
    private final boolean timedOut;
    private final boolean interrupted;
    private final Map<String, Object> data;
    private final String code;
    private final Boolean retryable;
    private final List<String> nextSteps;
    private final Map<String, Object> situation;

    /** Legacy five-field constructor — extension fields default to absent. */
    public TaskResult(boolean success, String message, boolean timedOut, boolean interrupted,
                      Map<String, Object> data) {
        this(success, message, timedOut, interrupted, data, null, null, null, null);
    }

    /** Full constructor. {@code null} extension fields are omitted from serialisation. */
    public TaskResult(boolean success, String message, boolean timedOut, boolean interrupted,
                      Map<String, Object> data, String code, Boolean retryable,
                      List<String> nextSteps, Map<String, Object> situation) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.timedOut = timedOut;
        this.interrupted = interrupted;
        this.data = data == null ? Map.of() : data;
        this.code = code;
        this.retryable = retryable;
        this.nextSteps = nextSteps == null || nextSteps.isEmpty() ? List.of() : List.copyOf(nextSteps);
        this.situation = situation == null || situation.isEmpty() ? Map.of() : situation;
    }

    public boolean success() {
        return success;
    }

    public String message() {
        return message;
    }

    public boolean timedOut() {
        return timedOut;
    }

    public boolean interrupted() {
        return interrupted;
    }

    public Map<String, Object> data() {
        return data;
    }

    /** Machine-readable error code (see {@link ErrorCode}); null on success or unclassified results. */
    public String code() {
        return code;
    }

    /** Whether retrying the same call as-is is permitted; null when unspecified. */
    public Boolean retryable() {
        return retryable;
    }

    /** Suggested next actions for the model; empty when unspecified. */
    public List<String> nextSteps() {
        return nextSteps;
    }

    /** Body situation snapshot at result time (see {@code BodySituation}); empty when unavailable. */
    public Map<String, Object> situation() {
        return situation;
    }

    public static TaskResult ok(String message, Map<String, Object> data) {
        return new TaskResult(true, message, false, false, data);
    }

    public static TaskResult ok(String message) {
        return new TaskResult(true, message, false, false, Map.of());
    }

    public static TaskResult fail(String message, Map<String, Object> data) {
        return new TaskResult(false, message, false, false, data);
    }

    public static TaskResult fail(String message) {
        return new TaskResult(false, message, false, false, Map.of());
    }

    public static TaskResult timeout(String message) {
        return new TaskResult(false, message, true, false, Map.of());
    }

    public static TaskResult cancelled(String message) {
        return new TaskResult(false, message, false, true, Map.of());
    }

    /** Cancellation with a structured reason (for example a body death). */
    public static TaskResult cancelled(String message, Map<String, Object> data) {
        return new TaskResult(false, message, false, true,
                data == null ? Map.of() : data);
    }

    /**
     * A copy of this result with the observability extension fields applied.
     * Legacy fields (success/message/timedOut/interrupted/data) are preserved
     * verbatim; {@code null} extension values leave the previous ones in place.
     */
    public TaskResult withObservability(String code, Boolean retryable,
                                        List<String> nextSteps, Map<String, Object> situation) {
        return new TaskResult(success, message, timedOut, interrupted, data,
                code != null ? code : this.code,
                retryable != null ? retryable : this.retryable,
                nextSteps != null && !nextSteps.isEmpty() ? nextSteps : this.nextSteps,
                situation != null && !situation.isEmpty() ? situation : this.situation);
    }

    /**
     * Render this result as the JSON string consumed by the LLM. The shape
     * mirrors the field names exactly so a model trained on common
     * tool-result conventions can read it without a custom system prompt.
     */
    public String toJson() {
        return GSON.toJson(toData());
    }

    /** JSON-friendly data that preserves nested maps, lists, and primitive types. */
    public Map<String, Object> toData() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", success);
        out.put("message", message);
        if (timedOut) out.put("timed_out", true);
        if (interrupted) out.put("interrupted", true);
        if (data != null && !data.isEmpty()) out.put("data", data);
        if (code != null) out.put("code", code);
        if (retryable != null) out.put("retryable", retryable);
        if (!nextSteps.isEmpty()) out.put("next_steps", new ArrayList<>(nextSteps));
        if (!situation.isEmpty()) out.put("situation", situation);
        return out;
    }

    @Override
    public String toString() {
        return "TaskResult{success=" + success + ", message=" + message
                + ", code=" + code + "}";
    }

    /**
     * Value semantics preserved from the legacy record shape: two results are
     * equal when their five core fields match. The observability extension
     * fields (code / retryable / next_steps / situation) are advisory and do
     * not affect equality, so a copy made with {@link #withObservability}
     * still equals its source — matching the pre-refactor record contract.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskResult other)) return false;
        return success == other.success
                && timedOut == other.timedOut
                && interrupted == other.interrupted
                && message.equals(other.message)
                && data.equals(other.data);
    }

    @Override
    public int hashCode() {
        int h = Boolean.hashCode(success);
        h = 31 * h + message.hashCode();
        h = 31 * h + Boolean.hashCode(timedOut);
        h = 31 * h + Boolean.hashCode(interrupted);
        h = 31 * h + data.hashCode();
        return h;
    }
}
