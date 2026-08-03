package com.dwinovo.numen.task;
import com.dwinovo.numen.task.TaskResult;

/**
 * One running task on a companion {@link com.dwinovo.numen.entity.NumenPlayer}
 * body, driven by {@code CompanionTickDispatcher}. The player-body replacement
 * for the Mob's {@code LlmTaskGoal} (which was a vanilla {@code Goal} run by a
 * GoalSelector) — here the dispatcher owns the lifecycle directly:
 * {@link #start()} once, {@link #tick()} each server tick until it returns a
 * terminal {@link TaskState}, then {@link #buildResult} for the reply.
 */
public interface CompanionTask {

    /** First-tick setup. May return a terminal state immediately via the record. */
    void start();

    /** Advance one tick. Returns {@link TaskState#RUNNING} or a terminal state. */
    TaskState tick();

    /** The result envelope handed back to the LLM. */
    TaskResult buildResult(TaskState finalState);

    /**
     * Release resources owned by a task without producing a result.
     *
     * <p>This is used by body lifecycle transitions (most notably death): the
     * death payload already resolves the in-flight tool call, so finalizing a
     * second result would be incorrect, but navigation/search handles and
     * physical action state must still be stopped. Implementations that do not
     * own resources may keep the default no-op.</p>
     */
    default void discard() {}
}
