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
                .build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, NumenPlayer companion, Consumer<String> reply) {
        String requested = args.has("task_id") && !args.get("task_id").isJsonNull()
                ? args.get("task_id").getAsString().trim() : "";
        TaskRecord rec = requested.isEmpty()
                ? CompanionTickDispatcher.asyncTaskFor(companion.getUUID())
                : CompanionTickDispatcher.taskById(companion.getUUID(), requested);
        if (rec == null) {
            String message = requested.isEmpty()
                    ? "身体空闲,没有后台任务。"
                    : "没有找到任务 " + requested + "；它可能不存在或已超出最近任务保留窗口。";
            reply.accept(TaskResult.ok(message, Map.of(
                    "state", requested.isEmpty() ? "idle" : "unknown",
                    "task_id", requested)).toJson());
            return;
        }
        long now = companion.level().getGameTime();
        TaskSnapshot snapshot = TaskSnapshot.capture(rec, now);
        reply.accept(TaskResult.ok(snapshot.summary(), snapshot.toData()).toJson());
    }
}
