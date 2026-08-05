package com.dwinovo.numen.task;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable public view of a pending, running, or recently completed task.
 *
 * <p>The snapshot deliberately contains no Minecraft classes, so the built-in
 * agent, MCP bridge, UI, and third-party integrations can share one contract.
 */
public record TaskSnapshot(String taskId,
                           String tool,
                           String description,
                           TaskState state,
                           long elapsedSeconds,
                           long budgetLeftSeconds,
                           TaskProgress progress,
                           TaskResult result,
                           long observedGameTime,
                           long stallWarningTicks) {

    public static TaskSnapshot capture(TaskRecord record, long nowGameTime) {
        long elapsed = record.getStartedGameTime() >= 0
                ? Math.max(0, nowGameTime - record.getStartedGameTime()) / 20 : 0;
        long budgetLeft = Math.max(0, record.getDeadlineGameTime() - nowGameTime) / 20;
        return new TaskSnapshot(
                record.publicId(),
                record.getToolName(),
                record.describe(),
                record.getState(),
                elapsed,
                budgetLeft,
                record.getProgress(),
                record.getResult(),
                nowGameTime,
                record.getState() == TaskState.RUNNING ? record.getStallWarningTicks() : 0);
    }

    public boolean terminal() {
        return state.isTerminal();
    }

    /** Stable JSON-friendly shape; nested maps remain structured in TaskResult. */
    public Map<String, Object> toData() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("task_id", taskId);
        out.put("task", tool);
        out.put("description", description);
        out.put("state", state.name().toLowerCase(Locale.ROOT));
        out.put("terminal", terminal());
        out.put("elapsed_s", elapsedSeconds);
        out.put("budget_left_s", budgetLeftSeconds);
        if (progress != null) {
            out.put("progress", progress.toData(observedGameTime, stallWarningTicks));
        }
        if (result != null) {
            out.put("result", result.toData());
            // Lift a situation snapshot (when the result carries one) to the snapshot
            // top level so task_status consumers see it without digging into result.
            if (!result.situation().isEmpty()) {
                out.put("situation", result.situation());
            }
        }
        return out;
    }

    public String summary() {
        if (terminal()) {
            String message = result == null ? "no result produced" : result.message();
            return taskId + "(" + description + ") "
                    + state.name().toLowerCase(Locale.ROOT) + ": " + message;
        }
        String phase = progress == null ? "running" : progress.phase();
        String detail = progress == null || progress.message().isBlank()
                ? "" : " — " + progress.message();
        return taskId + "(" + description + ") " + phase + detail
                + ", elapsed " + elapsedSeconds + "s, budget " + budgetLeftSeconds + "s";
    }
}
