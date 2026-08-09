package com.dwinovo.numen.core.task;

import com.dwinovo.numen.core.task.base.AbstractCompanionTask;
import com.dwinovo.numen.core.task.base.Precondition;
import com.dwinovo.numen.core.task.combat.CombatController;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.TaskState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit bounded combat task backed by the shared embodied controller. */
public final class CombatCompanionTask extends AbstractCompanionTask<CombatTaskRecord> {
    private final CombatController controller = new CombatController();
    private CombatController.Step lastStep;

    public CombatCompanionTask(NumenPlayer player, CombatTaskRecord record) {
        super(player, record);
    }

    @Override protected List<Precondition> preconditions() {
        return List.of(() -> player.getHealth() > r.fleeHealth ? null
                : new Precondition.Failure(
                        "combat cannot start at " + player.getHealth() + " health; recover above "
                                + r.fleeHealth + " first",
                        FailureType.LOW_HEALTH));
    }

    @Override protected TaskState onTick() {
        if (player.isDeadOrDying()) return TaskState.CANCELLED;
        if (player.getHealth() <= r.fleeHealth) {
            cancel("combat stopped because health reached the safety threshold " + r.fleeHealth,
                    FailureType.LOW_HEALTH);
            return TaskState.CANCELLED;
        }
        updateTerminalTargets();
        if (allTerminal()) return terminalState();

        lastStep = controller.tick(player, activeIds(), r.stance, r.maxRange, r.fleeHealth, "task");
        if (lastStep.escapeFailed() && lastStep.targetEntityId() != null) {
            r.lost(lastStep.targetEntityId());
            fail("combat stopped because the body could not escape the threat",
                    FailureType.BOXED_IN);
            return TaskState.FAILED;
        } else if (lastStep.unreachable() && lastStep.targetEntityId() != null) {
            r.unreachable(lastStep.targetEntityId());
            reportProgress("combat", "target proved unreachable", metrics());
        } else if (lastStep.attacked() && lastStep.targetEntityId() != null) {
            r.hit(lastStep.targetEntityId());
            reportProgress("combat", "attacked target", metrics());
        } else {
            reportActivity("combat", "executing " + lastStep.tactic().name().toLowerCase(java.util.Locale.ROOT),
                    metrics());
        }
        return TaskState.RUNNING;
    }

    private void updateTerminalTargets() {
        ServerLevel level = (ServerLevel) player.level();
        for (int id : r.entityIds) {
            if (r.terminal(id)) continue;
            Entity entity = level.getEntity(id);
            if (!(entity instanceof LivingEntity living) || entity == player || entity.isRemoved()) {
                r.lost(id);
            } else if (living.isDeadOrDying()) {
                r.defeated(id);
            } else if (player.distanceTo(living) > r.maxRange) {
                r.unreachable(id);
            }
        }
    }

    private List<Integer> activeIds() {
        return r.entityIds.stream().filter(id -> !r.terminal(id)).toList();
    }

    private boolean allTerminal() {
        return r.entityIds.stream().allMatch(r::terminal);
    }

    private TaskState terminalState() {
        if (!r.defeated().isEmpty()) return TaskState.SUCCESS;
        fail("none of the requested combat targets could be defeated", FailureType.TARGET_LOST);
        return TaskState.FAILED;
    }

    private Map<String, Object> metrics() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defeated", r.defeated().size());
        out.put("remaining", activeIds().size());
        if (lastStep != null) {
            out.put("tactic", lastStep.tactic().name().toLowerCase(java.util.Locale.ROOT));
            if (lastStep.targetEntityId() != null) out.put("target_entity_id", lastStep.targetEntityId());
        }
        return out;
    }

    @Override protected void cleanup() {
        controller.close(player, "task");
        super.cleanup();
    }

    @Override protected Map<String, Object> resultData() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requested_entity_ids", r.entityIds);
        out.put("defeated_entity_ids", r.defeated());
        out.put("lost_entity_ids", r.lost());
        out.put("unreachable_entity_ids", r.unreachable());
        out.put("hits", r.hits());
        out.put("stance", r.stance.wireName());
        out.put("max_range", r.maxRange);
        out.put("flee_health", r.fleeHealth);
        return out;
    }

    @Override protected String successMessage() {
        return "combat completed: defeated " + r.defeated().size() + "/" + r.entityIds.size();
    }

    @Override protected String timeoutMessage() {
        return "combat timed out after defeating " + r.defeated().size() + "/" + r.entityIds.size();
    }

    @Override protected String cancelledMessage() {
        return doneReason();
    }
}
