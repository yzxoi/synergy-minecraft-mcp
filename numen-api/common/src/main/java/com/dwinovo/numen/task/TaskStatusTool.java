package com.dwinovo.numen.task;

import com.dwinovo.numen.agent.tool.Schema;
import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.entity.NumenPlayer;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Consumer;

/** Query tool (instant): read the state of the background task, if any. */
public final class TaskStatusTool implements NumenTool {

    @Override
    public String name() {
        return "task_status";
    }

    @Override
    public String description() {
        return "Read a background task's structured snapshot: phase, progress metrics, stall signal, "
                + "elapsed/budget time, and terminal result. Pass task_id to retrieve a recently "
                + "completed external task; omit it for the body's current task.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object()
                .optionalString("task_id", "Optional task id returned when the action was accepted, e.g. t42. "
                        + "Use it to retrieve the exact terminal result after the body becomes idle.")
                .optionalInteger("wait_seconds", "Optional blocking wait: poll this task on the caller side "
                        + "until it reaches a terminal state or the budget elapses (0–60, default 0 = single "
                        + "snapshot). When the wait times out the snapshot carries timed_out_waiting=true and "
                        + "you may call again with the same task_id to continue waiting.", 0, 60)
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, NumenPlayer companion, Consumer<String> reply) {
        String requested = args.has("task_id") && !args.get("task_id").isJsonNull()
                ? args.get("task_id").getAsString().trim() : "";
        TaskRecord rec = requested.isEmpty()
                ? CompanionTickDispatcher.asyncTaskFor(companion.getUUID())
                : CompanionTickDispatcher.taskById(companion.getUUID(), requested);
        reply.accept(resultFor(rec, requested, companion.level().getGameTime()));
    }

    /**
     * Render a task lookup without requiring a Minecraft body.  Keeping the
     * response shaping here makes the terminal-state contract testable and
     * prevents future callers from collapsing a retained result back to the
     * ambiguous "body idle" response.
     */
    static String resultFor(TaskRecord rec, String requested, long nowGameTime) {
        if (rec == null) {
            String normalized = requested == null ? "" : requested;
            String message = normalized.isEmpty()
                    ? "The body is idle — no background task is running."
                    : "No task " + normalized + " found; it may not exist or has left the recent-task retention window.";
            return TaskResult.ok(message, Map.of(
                    "state", normalized.isEmpty() ? "idle" : "unknown",
                    "task_id", normalized)).toJson();
        }
        TaskSnapshot snapshot = TaskSnapshot.capture(rec, nowGameTime);
        return TaskResult.ok(snapshot.summary(), snapshot.toData()).toJson();
    }
}
