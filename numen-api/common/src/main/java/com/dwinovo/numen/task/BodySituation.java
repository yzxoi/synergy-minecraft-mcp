package com.dwinovo.numen.task;

import com.dwinovo.numen.entity.NumenPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A compact, transport-neutral snapshot of the body's current <em>situation</em>
 * — the danger/locomotion facts an external brain needs to react before reading
 * a full status dump. Attached to every {@link TaskResult} and
 * {@link TaskSnapshot} so the model never has to "remember" to call
 * {@code get_self_status} to learn it fell in water.
 *
 * <p>Pure JDK output shape (a flat snake_case map), so the same capture feeds
 * the built-in brain, the MCP envelope's {@code structuredContent}, and
 * situation-change event detection.
 */
public final class BodySituation {

    /** Radius for counting live monster-class entities and those actively targeting the body. */
    public static final double HOSTILE_SCAN_RADIUS = 16.0;
    /** Server-side attacker memory window: five seconds at the normal 20 TPS. */
    public static final int ATTACK_MEMORY_TICKS = 100;

    /** Air ticks below this are reported as {@code air_low} by the situation tracker. */
    public static final int AIR_LOW_TICKS = 90;

    private BodySituation() {}

    /** Capture the body's situation right now. Never throws — a missing field degrades to a sane default. */
    public static Map<String, Object> capture(Player body) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.put("in_water", body.isInWater());
            out.put("eye_underwater", body.isEyeInFluid(FluidTags.WATER));
            int air = body.getAirSupply();
            int maxAir = body.getMaxAirSupply();
            out.put("air", air);
            out.put("air_pct", maxAir <= 0 ? 0 : Math.max(0, Math.min(100, air * 100 / maxAir)));
            out.put("on_ground", body.onGround());
            out.put("hp", (double) body.getHealth());
            out.put("max_hp", (double) body.getMaxHealth());
            out.put("hunger", body.getFoodData().getFoodLevel());
            out.put("in_lava", body.isInLava());
            out.put("dimension", body.level().dimension().identifier().toString());
            out.put("x", body.getX());
            out.put("y", body.getY());
            out.put("z", body.getZ());
            out.put("locomotion", locomotion(body));
            out.put("active_reflex", activeReflex(body));
            addDanger(out, body);
        } catch (RuntimeException ignored) {
            // A situation capture must never take a tool result down with it.
        }
        return out;
    }

    private static void addDanger(Map<String, Object> out, Player body) {
        try {
            var box = body.getBoundingBox().inflate(HOSTILE_SCAN_RADIUS);
            double radiusSq = HOSTILE_SCAN_RADIUS * HOSTILE_SCAN_RADIUS;
            var hostiles = body.level().getEntitiesOfClass(Monster.class, box,
                    monster -> !monster.isRemoved() && !monster.isDeadOrDying()
                            && monster.distanceToSqr(body) <= radiusSq);
            int targeting = 0;
            for (Monster hostile : hostiles) {
                if (hostile.getTarget() == body) targeting++;
            }
            int hurtTimestamp = body.getLastHurtByMobTimestamp();
            boolean hasRecentAttacker = body.getLastHurtByMob() != null;
            out.put("nearby_hostiles", hostiles.size());
            out.put("targeting_hostiles", targeting);
            out.put("under_attack", underAttack(body.tickCount, hurtTimestamp,
                    hasRecentAttacker, body.hurtTime, targeting));
        } catch (RuntimeException ignored) {
            out.putIfAbsent("nearby_hostiles", 0);
            out.putIfAbsent("targeting_hostiles", 0);
            out.putIfAbsent("under_attack", false);
        }
    }

    static boolean underAttack(int currentTick, int hurtTimestamp, boolean hasRecentAttacker,
                               int hurtTime, int targetingHostiles) {
        int ticksSinceHurt = currentTick - hurtTimestamp;
        boolean recentlyHurt = hasRecentAttacker && ticksSinceHurt >= 0
                && ticksSinceHurt <= ATTACK_MEMORY_TICKS;
        return targetingHostiles > 0 || hurtTime > 0 || recentlyHurt;
    }

    private static String locomotion(Player body) {
        try {
            if (body.isFallFlying()) return "elytra_flying";
            if (body.isSwimming()) return "swimming";
            if (body.isInLava()) return "in_lava";
            if (body.isInWater()) return "in_water";
            if (body.onGround()) return "on_ground";
            return "airborne";
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    /** The currently scheduled chain (the active reflex / survival instinct), or "none". */
    private static String activeReflex(Player body) {
        try {
            if (!(body instanceof NumenPlayer companion)) return "unknown";
            String chain = CompanionTickDispatcher.runtimeSnapshot(companion).chain();
            return chain == null || chain.isBlank() ? "none" : chain;
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }
}
