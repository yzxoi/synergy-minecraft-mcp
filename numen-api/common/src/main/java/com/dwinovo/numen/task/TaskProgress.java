package com.dwinovo.numen.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live, transport-neutral progress for one task.
 *
 * <p>{@code updatedGameTime} moves whenever the task reports a fresh observation;
 * {@code advancedGameTime} moves only when a material progress metric improves.
 * Keeping the two clocks separate lets an external driver distinguish a task that
 * is alive but stalled from one whose scheduler stopped ticking altogether.
 */
public record TaskProgress(String phase,
                           String message,
                           Map<String, Object> metrics,
                           long updatedGameTime,
                           long advancedGameTime) {

    public TaskProgress {
        phase = phase == null || phase.isBlank() ? "running" : phase;
        message = message == null ? "" : message;
        metrics = metrics == null || metrics.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
    }

    /** Initial progress stamped when the scheduler first starts the task. */
    public static TaskProgress started(long gameTime) {
        return new TaskProgress("starting", "task started", Map.of(), gameTime, gameTime);
    }

    /** JSON-friendly representation used by task_status and future transports. */
    public Map<String, Object> toData(long nowGameTime, long stallWarningTicks) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phase", phase);
        if (!message.isBlank()) out.put("message", message);
        if (!metrics.isEmpty()) out.put("metrics", metrics);
        if (updatedGameTime >= 0) {
            out.put("seconds_since_update", Math.max(0, nowGameTime - updatedGameTime) / 20);
        }
        if (advancedGameTime >= 0) {
            long staleTicks = Math.max(0, nowGameTime - advancedGameTime);
            out.put("seconds_since_progress", staleTicks / 20);
            if (stallWarningTicks > 0) {
                out.put("stalled", staleTicks >= stallWarningTicks);
                out.put("stall_warning_after_s", stallWarningTicks / 20);
            }
        }
        return out;
    }
}
