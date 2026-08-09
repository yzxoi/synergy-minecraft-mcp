package com.dwinovo.numen.core.task.chain;

import com.dwinovo.numen.core.task.CombatTaskRecord;
import com.dwinovo.numen.core.task.SurvivalConfig;
import com.dwinovo.numen.core.task.combat.CombatController;
import com.dwinovo.numen.core.task.combat.CombatStance;
import com.dwinovo.numen.core.task.combat.TacticalDecisions;
import com.dwinovo.numen.core.task.survival.SurvivalDecisions;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.BodyLog;
import com.dwinovo.numen.task.CompanionTickDispatcher;
import com.dwinovo.numen.task.TaskChain;
import com.dwinovo.numen.task.TaskRecord;
import com.dwinovo.numen.task.reflex.Reflex;
import net.minecraft.tags.FluidTags;

/** Autonomous defensive reflex backed by the same embodied controller as the combat tool. */
public final class TacticalCombatChain implements TaskChain, Reflex {
    private static final double SCAN_RADIUS = 12.0;
    private final BodyLog bodyLog;
    private final CombatController controller = new CombatController();
    private Integer episodeTarget;

    public TacticalCombatChain() { this(null); }
    public TacticalCombatChain(BodyLog bodyLog) { this.bodyLog = bodyLog; }

    @Override public float getPriority(NumenPlayer companion) {
        boolean enabled = SurvivalConfig.enabled()
                && com.dwinovo.numen.task.reflex.ReflexRegistry.enabled(id());
        TaskRecord active = CompanionTickDispatcher.asyncTaskFor(companion.getUUID());
        boolean explicitCombat = active instanceof CombatTaskRecord;
        boolean threat = enabled && !explicitCombat
                && controller.probe(companion, null, CombatStance.DEFENSIVE, SCAN_RADIUS);
        return TacticalDecisions.priority(threat, enabled,
                companion.isEyeInFluid(FluidTags.WATER), companion.getAirSupply(),
                controller.cooldownActive(companion), explicitCombat);
    }

    @Override public void tick(NumenPlayer companion) {
        CombatController.Step step = controller.tick(companion, null, CombatStance.DEFENSIVE,
                SCAN_RADIUS, SurvivalDecisions.FLEE_HEALTH, "reflex");
        if (episodeTarget == null) episodeTarget = step.targetEntityId();
        if (!step.active() && episodeTarget != null) closeEpisode(companion);
    }

    @Override public void onInterrupt(NumenPlayer companion) {
        controller.close(companion, "reflex");
        episodeTarget = null;
    }

    private void closeEpisode(NumenPlayer companion) {
        if (bodyLog != null) bodyLog.report("survived a nearby hostile encounter");
        episodeTarget = null;
    }

    @Override public String name() { return "combat"; }
    @Override public String id() { return "mob_defense"; }
    @Override public String describe() {
        return "tracks nearby attackers on a bounded threat blackboard and fights, kites, or flees safely";
    }
}
