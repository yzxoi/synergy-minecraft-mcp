package com.dwinovo.numen.core.task.combat;

import com.dwinovo.numen.core.task.survival.SurvivalDecisions;

import java.util.ArrayList;
import java.util.List;

/** Pure deterministic tactical policy. All world access stays in {@link CombatController}. */
public final class TacticalDecisions {
    public static final double CRITICAL_HEALTH = 4.0;
    public static final double MULTI_THREAT_FLEE_HEALTH = 12.0;

    private TacticalDecisions() {}

    public static boolean qualifies(boolean attackedUs, boolean targetsUs, CombatStance stance) {
        return stance == CombatStance.AGGRESSIVE || attackedUs || targetsUs;
    }

    /** Lower scores are more urgent; the recent attacker receives the legacy one-block bias. */
    public static double threatScore(ThreatDatum threat) {
        return threat.distance() - (threat.hurtByRecency() ? 1.0 : 0.0);
    }

    public static double velocity(double previous, double current, long deltaTicks) {
        if (deltaTicks <= 0) return 0.0;
        return (current - previous) * 20.0 / deltaTicks;
    }

    /** Minecraft yaw convention: 0=south, -90=east. */
    public static double bearing(double dx, double dz, double yawDegrees) {
        double raw = Math.toDegrees(Math.atan2(-dx, dz)) - yawDegrees;
        while (raw >= 180.0) raw -= 360.0;
        while (raw < -180.0) raw += 360.0;
        return raw;
    }

    public static TacticalIntent decide(BodyDatum body, ThreatDatum threat,
                                        boolean inReach, boolean attackReady,
                                        CombatStance stance) {
        return decide(body, threat, inReach, attackReady, stance, SurvivalDecisions.FLEE_HEALTH);
    }

    public static TacticalIntent decide(BodyDatum body, ThreatDatum threat,
                                        boolean inReach, boolean attackReady,
                                        CombatStance stance, double fleeHealth) {
        if (threat == null || threat.removed()) return TacticalIntent.disengage();
        if (body.inLava() || body.health() <= fleeHealth || !body.armed()
                || (body.activeThreats() >= 2 && body.health() <= MULTI_THREAT_FLEE_HEALTH)) {
            return TacticalIntent.flee(threat.entityId());
        }
        if (stance == CombatStance.KITE || threat.rangedAttacker()) {
            return new TacticalIntent(CombatTactic.KITE, false, threat.entityId());
        }
        if (inReach && threat.lineOfSight()) {
            if (attackReady) {
                return new TacticalIntent(CombatTactic.ATTACK, true, threat.entityId());
            }
            return new TacticalIntent(CombatTactic.STRAFE, false, threat.entityId());
        }
        return new TacticalIntent(CombatTactic.CHASE, false, threat.entityId());
    }

    /** A chase is complete only when attack reach and line of sight are both available. */
    public static boolean chaseGoalReached(boolean targetRemoved, boolean inReach,
                                           boolean lineOfSight) {
        return targetRemoved || (inReach && lineOfSight);
    }

    public static ShieldedIntent shield(TacticalIntent proposed, BodyDatum body, boolean targetPresent) {
        return shield(proposed, body, targetPresent, SurvivalDecisions.FLEE_HEALTH);
    }

    public static ShieldedIntent shield(TacticalIntent proposed, BodyDatum body, boolean targetPresent,
                                        double fleeHealth) {
        List<String> filters = new ArrayList<>();
        TacticalIntent safe = proposed;
        if (!targetPresent) {
            filters.add("target_gone");
            safe = TacticalIntent.disengage();
        } else if (body.inLava()) {
            filters.add("lava");
            safe = TacticalIntent.flee(proposed.targetEntityId());
        } else if (body.health() <= CRITICAL_HEALTH) {
            filters.add("critical_health");
            safe = TacticalIntent.flee(proposed.targetEntityId());
        } else if (proposed.attack() && (body.health() <= fleeHealth || !body.armed())) {
            filters.add(body.armed() ? "low_health" : "unarmed");
            safe = TacticalIntent.flee(proposed.targetEntityId());
        }
        return new ShieldedIntent(safe, filters);
    }

    public static double strafeOffset(int entityId) {
        return 1.5 + Math.floorMod(entityId, 2);
    }

    public static int strafeSide(long gameTime, int entityId) {
        return Math.floorMod((int) (gameTime / 20L) + entityId, 2) == 0 ? 1 : -1;
    }

    public static float priority(boolean threatPresent, boolean enabled,
                                 boolean headUnderWater, int airSupply,
                                 boolean cooldownActive, boolean explicitCombatActive) {
        if (!enabled || !threatPresent || cooldownActive || explicitCombatActive) {
            return SurvivalDecisions.DORMANT;
        }
        if (headUnderWater && airSupply <= SurvivalDecisions.LOW_AIR_TICKS) {
            return SurvivalDecisions.DORMANT;
        }
        return SurvivalDecisions.MOB_DEFENSE_PRIORITY;
    }
}
