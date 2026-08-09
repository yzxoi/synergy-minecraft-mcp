package com.dwinovo.numen.core.task.combat;

import com.dwinovo.numen.core.act.Interaction;
import com.dwinovo.numen.core.pathing.calc.NavGoal;
import com.dwinovo.numen.core.pathing.exec.PlayerNav;
import com.dwinovo.numen.core.task.base.ToolSelect;
import com.dwinovo.numen.entity.InputDriver;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.event.GameEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World-bound embodied combat loop: observe → remember → decide → safety-shield → act.
 * The policy is pure ({@link TacticalDecisions}); this class alone owns Minecraft
 * references, navigation, locomotion inputs, attack execution and status projection.
 */
public final class CombatController {
    public record Step(CombatTactic tactic, Integer targetEntityId, boolean attacked,
                       boolean unreachable, boolean escapeFailed, boolean active) {}

    private static final double ATTACK_REACH = 3.0;
    private static final double CHASE_SPEED = 1.2;
    private static final double FLEE_SPEED = 1.3;
    private static final int MAX_NAV_FAILURES = 3;
    private static final long UNREACHABLE_COOLDOWN = 200;
    private static final long CONTROLLER_COOLDOWN = 100;

    private final ThreatBlackboard blackboard = new ThreatBlackboard();
    private final Map<Integer, Long> unreachableUntil = new HashMap<>();
    private final Map<Integer, Integer> navFailures = new HashMap<>();

    private PlayerNav nav;
    private CombatTactic navTactic;
    private Integer navTargetId;
    private long cooldownUntil;
    private Integer announcedTarget;
    private CombatTactic announcedTactic = CombatTactic.DISENGAGE;

    public boolean probe(NumenPlayer player, List<Integer> authorizedIds,
                         CombatStance stance, double maxRange) {
        return !observe(player, authorizedIds, stance, maxRange).isEmpty();
    }

    public boolean cooldownActive(NumenPlayer player) {
        return player.level().getGameTime() < cooldownUntil;
    }

    public Step tick(NumenPlayer player, List<Integer> authorizedIds, CombatStance stance,
                     double maxRange, double fleeHealth, String source) {
        long now = player.level().getGameTime();
        List<ThreatDatum> observed = observe(player, authorizedIds, stance, maxRange);
        ThreatDatum threat = observed.stream()
                .sorted(java.util.Comparator.comparingDouble(TacticalDecisions::threatScore)
                        .thenComparingInt(ThreatDatum::entityId))
                .findFirst().orElse(null);
        LivingEntity target = threat == null ? null : liveTarget(player, threat.entityId());
        BodyDatum body = new BodyDatum(player.getHealth(), ToolSelect.hasMeleeWeapon(player),
                player.isInLava(), observed.size());
        boolean inReach = target != null && player.distanceToSqr(target) <= ATTACK_REACH * ATTACK_REACH;
        boolean attackReady = player.getAttackStrengthScale(0.0f) >= 0.99f;
        TacticalIntent proposed = TacticalDecisions.decide(body, threat, inReach, attackReady,
                stance, fleeHealth);
        ShieldedIntent shielded = TacticalDecisions.shield(proposed, body, target != null, fleeHealth);
        TacticalIntent intent = shielded.intent();
        boolean attacked = false;
        boolean unreachable = false;
        boolean escapeFailed = false;

        if (cooldownActive(player) || target == null || intent.tactic() == CombatTactic.DISENGAGE) {
            halt(player);
            intent = TacticalIntent.disengage();
        } else {
            switch (intent.tactic()) {
                case ATTACK -> {
                    navFailures.remove(target.getId());
                    halt(player);
                    ToolSelect.holdBestWeapon(player);
                    Interaction.attackEntity(player, target).tick();
                    attacked = attackReady;
                }
                case CHASE -> unreachable = tickChase(player, target, now);
                case FLEE -> escapeFailed = tickFlee(player, target, now);
                case STRAFE -> {
                    navFailures.remove(target.getId());
                    strafe(player, target, false, now);
                }
                case KITE -> {
                    navFailures.remove(target.getId());
                    strafe(player, target, true, now);
                }
                case DISENGAGE -> halt(player);
            }
        }

        publish(player, source, stance, intent, maxRange, fleeHealth, shielded.filtersApplied(), now);
        emitTransition(player, source, intent);
        return new Step(intent.tactic(), intent.targetEntityId(), attacked, unreachable,
                escapeFailed, intent.tactic() != CombatTactic.DISENGAGE);
    }

    public void close(NumenPlayer player, String source) {
        halt(player);
        blackboard.clear();
        CombatStatusRegistry.publish(player.getUUID(),
                CombatStatusSnapshot.idle(player.getHealth(), player.level().getGameTime()));
        if (announcedTarget != null) {
            GameEvents.emit(player, GameEvents.Kind.COMBAT,
                    Map.of("source", source, "status", "disengaged"), "combat controller disengaged");
        }
        announcedTarget = null;
        announcedTactic = CombatTactic.DISENGAGE;
    }

    private List<ThreatDatum> observe(NumenPlayer player, List<Integer> authorizedIds,
                                      CombatStance stance, double maxRange) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        LivingEntity attacker = player.getLastHurtByMob();
        List<LivingEntity> candidates = new ArrayList<>();
        if (authorizedIds == null) {
            AABB box = player.getBoundingBox().inflate(maxRange);
            candidates.addAll(level.getEntitiesOfClass(Monster.class, box));
        } else {
            for (int id : authorizedIds) {
                Entity entity = level.getEntity(id);
                if (entity instanceof LivingEntity living && living != player) candidates.add(living);
            }
        }

        Map<Integer, ThreatDatum> previous = new HashMap<>();
        for (ThreatDatum threat : blackboard.threats()) previous.put(threat.entityId(), threat);
        List<ThreatDatum> observed = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (LivingEntity living : candidates) {
            if (!seen.add(living.getId()) || living.isRemoved() || living.isDeadOrDying()) continue;
            double distance = player.distanceTo(living);
            if (distance > maxRange) continue;
            boolean targetsUs = living instanceof Monster monster && monster.getTarget() == player;
            boolean attackedUs = living == attacker;
            if (!TacticalDecisions.qualifies(attackedUs, targetsUs, stance)) continue;
            if (unreachableUntil.getOrDefault(living.getId(), 0L) > now) continue;

            ThreatDatum old = previous.get(living.getId());
            long delta = old == null ? 0 : now - old.lastSeenGameTime();
            Vec3 pos = living.position();
            double vx = old == null ? 0 : TacticalDecisions.velocity(old.x(), pos.x, delta);
            double vy = old == null ? 0 : TacticalDecisions.velocity(old.y(), pos.y, delta);
            double vz = old == null ? 0 : TacticalDecisions.velocity(old.z(), pos.z, delta);
            Vec3 offset = pos.subtract(player.position());
            observed.add(new ThreatDatum(living.getId(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString(),
                    pos.x, pos.y, pos.z, vx, vy, vz, distance,
                    TacticalDecisions.bearing(offset.x, offset.z, player.getYRot()),
                    player.hasLineOfSight(living), now, attackedUs, attackedUs || targetsUs,
                    living instanceof RangedAttackMob, living.getHealth(), false));
        }
        blackboard.update(observed, now);
        unreachableUntil.entrySet().removeIf(e -> e.getValue() <= now);
        Set<Integer> observedIds = new HashSet<>();
        for (ThreatDatum threat : observed) observedIds.add(threat.entityId());
        navFailures.keySet().removeIf(id -> !observedIds.contains(id));
        return observed;
    }

    private boolean tickChase(NumenPlayer player, LivingEntity target, long now) {
        ensureNav(player, CombatTactic.CHASE, target, () -> PlayerNav.followEntity(player,
                () -> target, 2.0, CHASE_SPEED,
                () -> TacticalDecisions.chaseGoalReached(target.isRemoved(),
                        player.distanceToSqr(target) <= ATTACK_REACH * ATTACK_REACH,
                        player.hasLineOfSight(target))));
        return tickNav(player, target.getId(), now, false,
                () -> TacticalDecisions.chaseGoalReached(target.isRemoved(),
                        player.distanceToSqr(target) <= ATTACK_REACH * ATTACK_REACH,
                        player.hasLineOfSight(target)), true);
    }

    private boolean tickFlee(NumenPlayer player, LivingEntity target, long now) {
        ensureNav(player, CombatTactic.FLEE, target, () -> PlayerNav.toGoal(player,
                () -> NavGoal.runAway(target.blockPosition(), player.blockPosition().getY()),
                FLEE_SPEED, () -> target.isRemoved()));
        return tickNav(player, target.getId(), now, true, target::isRemoved, false);
    }

    private void ensureNav(NumenPlayer player, CombatTactic tactic, LivingEntity target,
                           java.util.function.Supplier<PlayerNav> factory) {
        if (nav == null || navTactic != tactic || !java.util.Objects.equals(navTargetId, target.getId())) {
            stopNav();
            nav = factory.get();
            navTactic = tactic;
            navTargetId = target.getId();
        }
    }

    private boolean tickNav(NumenPlayer player, int targetId, long now, boolean fleeing,
                            java.util.function.BooleanSupplier arrivalAccepted,
                            boolean invalidArrivalIsFailure) {
        PlayerNav.Status status = nav.tick();
        boolean accepted = status == PlayerNav.Status.ARRIVED && arrivalAccepted.getAsBoolean();
        int failures = navFailureCountAfter(navFailures.getOrDefault(targetId, 0), status,
                accepted, invalidArrivalIsFailure);
        if (failures == 0) navFailures.remove(targetId);
        else navFailures.put(targetId, failures);

        if (status == PlayerNav.Status.RUNNING || accepted) return false;
        stopNav();
        if (!navFailureLimitReached(failures)) return false;
        navFailures.remove(targetId);
        if (fleeing) cooldownUntil = now + CONTROLLER_COOLDOWN;
        else unreachableUntil.put(targetId, now + UNREACHABLE_COOLDOWN);
        InputDriver.halt(player);
        return true;
    }

    static int navFailureCountAfter(int current, PlayerNav.Status status, boolean arrivalAccepted,
                                    boolean invalidArrivalIsFailure) {
        return switch (status) {
            case RUNNING -> current;
            case ARRIVED -> arrivalAccepted || !invalidArrivalIsFailure ? 0 : current + 1;
            case FAILED -> current + 1;
        };
    }

    static boolean navFailureLimitReached(int failures) {
        return failures >= MAX_NAV_FAILURES;
    }


    private void strafe(NumenPlayer player, LivingEntity target, boolean kite, long now) {
        stopNav();
        InputDriver.lookAt(player, target.getEyePosition());
        player.xxa = TacticalDecisions.strafeSide(now, target.getId());
        player.zza = kite ? -0.6f : 0.0f;
        player.setSprinting(kite && !player.isShiftKeyDown());
    }

    private LivingEntity liveTarget(NumenPlayer player, int id) {
        Entity entity = ((ServerLevel) player.level()).getEntity(id);
        if (entity instanceof LivingEntity living && living != player
                && !living.isRemoved() && !living.isDeadOrDying()) return living;
        return null;
    }

    private void publish(NumenPlayer player, String source, CombatStance stance,
                         TacticalIntent intent, double maxRange, double fleeHealth,
                         List<String> filters, long now) {
        CombatStatusRegistry.publish(player.getUUID(), new CombatStatusSnapshot(
                intent.tactic() != CombatTactic.DISENGAGE, source, stance.wireName(),
                intent.tactic().name().toLowerCase(java.util.Locale.ROOT), intent.targetEntityId(),
                player.getHealth(), maxRange, fleeHealth, blackboard.threats(), filters, now));
    }

    private void emitTransition(NumenPlayer player, String source, TacticalIntent intent) {
        if (java.util.Objects.equals(announcedTarget, intent.targetEntityId())
                && announcedTactic == intent.tactic()) return;
        announcedTarget = intent.targetEntityId();
        announcedTactic = intent.tactic();
        Map<String, String> attrs = new java.util.LinkedHashMap<>();
        attrs.put("source", source);
        attrs.put("tactic", intent.tactic().name().toLowerCase(java.util.Locale.ROOT));
        if (intent.targetEntityId() != null) attrs.put("target_entity_id", intent.targetEntityId().toString());
        GameEvents.emit(player, GameEvents.Kind.COMBAT, attrs,
                "combat tactic changed to " + attrs.get("tactic"));
    }

    private void halt(NumenPlayer player) {
        stopNav();
        InputDriver.halt(player);
        player.setShiftKeyDown(false);
    }

    private void stopNav() {
        if (nav != null) nav.stop();
        nav = null;
        navTactic = null;
        navTargetId = null;
    }
}
