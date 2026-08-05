package com.dwinovo.numen.event;

import com.dwinovo.numen.entity.NumenPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detects body situation <em>changes</em> on the server tick and mirrors them
 * into the companion's event ring ({@link EventChannels}) as semantic events:
 * {@code entered_water / left_water / air_low / damaged / fell}.
 *
 * <p>Debounce: the same event kind fires at most once per {@link #MIN_INTERVAL_TICKS}
 * (default 40 ticks = 2 s), so drowning air ticks or repeated small falls do not
 * storm the channel. The detector is a pure per-companion state holder; the
 * {@code CompanionBrain} calls {@link #tick(NumenPlayer)} every server tick.
 *
 * <p>RESPAWNED is not detected here — the lifecycle wiring fires it directly via
 * {@link GameEvents} when a dead body respawns (see {@code Companions.respawnDead}).
 */
public final class SituationTracker {

    /** Minimum ticks between two events of the same kind (2 s). */
    public static final long MIN_INTERVAL_TICKS = 40;

    /** Fall distance (blocks) at or above which a FELL event fires. */
    public static final double FALL_EVENT_THRESHOLD = 3.0;

    /** HP loss (half-hearts) at or above which a DAMAGED event fires. */
    public static final double DAMAGE_EVENT_THRESHOLD = 2.0;

    private boolean wasInWater;
    private boolean wasAirLow;
    private double lastHp = Double.NaN;
    private double lastY;
    private boolean lastOnGround;
    private long lastKindTick = Long.MIN_VALUE;
    private String lastKind;

    /**
     * Run one detection pass. Call once per server tick per companion; the body
     * argument must be the same companion every time.
     */
    public void tick(NumenPlayer body) {
        long now = body.level().getGameTime();
        boolean inWater = body.isInWater();
        boolean airLow = body.getAirSupply() <= com.dwinovo.numen.task.BodySituation.AIR_LOW_TICKS;
        double hp = body.getHealth();
        double y = body.getY();
        boolean onGround = body.onGround();

        if (inWater && !wasInWater) {
            fire(body, now, GameEvents.Kind.ENTERED_WATER, Map.of());
        } else if (!inWater && wasInWater) {
            fire(body, now, GameEvents.Kind.LEFT_WATER, Map.of());
        }

        if (airLow && !wasAirLow) {
            fire(body, now, GameEvents.Kind.AIR_LOW, Map.of("air", body.getAirSupply()));
        }

        if (!Double.isNaN(lastHp) && hp < lastHp - DAMAGE_EVENT_THRESHOLD) {
            fire(body, now, GameEvents.Kind.DAMAGED, Map.of(
                    "from_hp", lastHp, "to_hp", hp));
        }

        if (lastOnGround && !onGround && y < lastY - FALL_EVENT_THRESHOLD) {
            fire(body, now, GameEvents.Kind.FELL, Map.of(
                    "fell_blocks", Math.round((lastY - y) * 10) / 10.0));
        }

        wasInWater = inWater;
        wasAirLow = airLow;
        lastHp = hp;
        lastY = y;
        lastOnGround = onGround;
    }

    private void fire(NumenPlayer body, long now, GameEvents.Kind kind, Map<String, Object> data) {
        if (kind.kindName().equals(lastKind) && now - lastKindTick < MIN_INTERVAL_TICKS) {
            return;   // debounce the same kind
        }
        lastKind = kind.kindName();
        lastKindTick = now;
        Map<String, Object> payload = new LinkedHashMap<>(data);
        EventChannels.append(body, kind.kindName(), payload);
    }
}
