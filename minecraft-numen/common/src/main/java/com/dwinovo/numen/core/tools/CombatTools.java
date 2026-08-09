package com.dwinovo.numen.core.tools;

import com.dwinovo.numen.agent.tool.api.ToolContext;
import com.dwinovo.numen.core.task.CombatTaskRecord;
import com.dwinovo.numen.core.task.combat.CombatStance;
import com.dwinovo.numen.core.task.MeleeAttackTaskRecord;
import com.dwinovo.numen.core.task.RangedAttackTaskRecord;
import com.dwinovo.numen.task.TaskRecord;

import java.util.List;

/** Creates typed records for explicit melee, ranged, and tactical combat tasks. */
public final class CombatTools {

    public TaskRecord meleeAttack(List<Integer> entityIds, ToolContext ctx) {
        List<Integer> ids = normalizeEntityIds(entityIds);
        long timeout = Math.min(10L * 60L * 20L,
                Math.max(120L * 20L, ids.size() * 60L * 20L));
        return new MeleeAttackTaskRecord(ctx.toolCallId(), ctx.deadline(timeout), ids);
    }

    public TaskRecord rangedAttack(List<Integer> entityIds, ToolContext ctx) {
        List<Integer> ids = normalizeEntityIds(entityIds);
        long timeout = Math.min(10L * 60L * 20L,
                Math.max(120L * 20L, ids.size() * 75L * 20L));
        return new RangedAttackTaskRecord(ctx.toolCallId(), ctx.deadline(timeout), ids);
    }

    public TaskRecord combat(List<Integer> entityIds, CombatStance stance,
                             double maxRange, double fleeHealth, int maxSeconds,
                             ToolContext ctx) {
        List<Integer> ids = normalizeEntityIds(entityIds);
        if (maxRange < 4.0 || maxRange > 32.0) {
            throw new IllegalArgumentException("max_range must be between 4 and 32");
        }
        if (fleeHealth < 4.0 || fleeHealth > 16.0) {
            throw new IllegalArgumentException("flee_health must be between 4 and 16");
        }
        if (maxSeconds < 30 || maxSeconds > 600) {
            throw new IllegalArgumentException("max_seconds must be between 30 and 600");
        }
        return new CombatTaskRecord(ctx.toolCallId(), ctx.deadline(maxSeconds * 20L), ids,
                stance == null ? CombatStance.DEFENSIVE : stance, maxRange, fleeHealth);
    }

    static List<Integer> normalizeEntityIds(List<Integer> entityIds) {
        if (entityIds == null) throw new IllegalArgumentException("entity_ids is required");
        List<Integer> ids = entityIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("entity_ids contained no runtime entity ids");
        }
        if (ids.size() > 20) {
            throw new IllegalArgumentException("entity_ids accepts at most 20 distinct ids");
        }
        return ids;
    }
}
