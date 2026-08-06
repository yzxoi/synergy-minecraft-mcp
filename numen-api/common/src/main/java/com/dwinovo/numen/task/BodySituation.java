package com.dwinovo.numen.task;

import com.dwinovo.numen.entity.NumenPlayer;
import net.minecraft.tags.FluidTags;

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

    /** Air ticks below this are reported as {@code air_low} by the situation tracker. */
    public static final int AIR_LOW_TICKS = 90;

    private BodySituation() {}

    /** Capture the body's situation right now. Never throws — a missing field degrades to a sane default. */
    public static Map<String, Object> capture(NumenPlayer body) {
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
            out.put("hunger", body.getFoodData().getFoodLevel());
            out.put("in_lava", body.isInLava());
            out.put("dimension", body.level().dimension().identifier().toString());
            out.put("x", body.getX());
            out.put("y", body.getY());
            out.put("z", body.getZ());
            out.put("locomotion", locomotion(body));
            out.put("active_reflex", activeReflex(body));
        } catch (RuntimeException ignored) {
            // A situation capture must never take a tool result down with it.
        }
        return out;
    }

    private static String locomotion(NumenPlayer body) {
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
    private static String activeReflex(NumenPlayer body) {
        try {
            String chain = CompanionTickDispatcher.runtimeSnapshot(body).chain();
            return chain == null || chain.isBlank() ? "none" : chain;
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }
}
