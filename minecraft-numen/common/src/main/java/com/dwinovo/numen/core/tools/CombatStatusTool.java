package com.dwinovo.numen.core.tools;

import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.agent.tool.Schema;
import com.dwinovo.numen.core.task.combat.CombatStatusRegistry;
import com.dwinovo.numen.core.task.combat.CombatStatusSnapshot;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.TaskResult;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Consumer;

/** Read-only instantaneous view of the embodied combat controller. */
public final class CombatStatusTool implements NumenTool {
    @Override public String name() { return "combat_status"; }

    @Override public String description() {
        return "Read the latest embodied combat state: active source, stance, selected tactic/target, "
                + "bounded threat blackboard, health thresholds, and safety filters. No arguments.";
    }

    @Override public Map<String, Object> parameterSchema() { return Schema.none(); }

    @Override public void onServerCall(String toolCallId, JsonObject args, NumenPlayer companion,
                                       Consumer<String> reply) {
        CombatStatusSnapshot snapshot = CombatStatusRegistry.get(companion.getUUID());
        if (snapshot == null) {
            snapshot = CombatStatusSnapshot.idle(companion.getHealth(), companion.level().getGameTime());
        }
        reply.accept(TaskResult.ok(snapshot.active() ? "combat controller active" : "combat controller idle",
                snapshot.toData()).toJson());
    }
}
