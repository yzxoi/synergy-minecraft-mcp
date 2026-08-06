package com.dwinovo.numen.event;

import com.dwinovo.numen.entity.NumenPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>Falls are measured from the {@code y} at the moment the body leaves the
 * ground, not from the previous tick's {@code y} — gravity moves a body only a
 * fraction of a block per tick, so comparing against the last tick could never
 * accumulate a 3-block fall.
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

    /** One detected situation change, before debounce. */
    record Event(GameEvents.Kind kind, Map<String, Object> data) {}

    private boolean wasInWater;
    private boolean wasAirLow;
    private double lastHp = Double.NaN;
    private boolean lastOnGround;
    /** Y at the moment the body left the ground; NaN while grounded or after an event fired. */
    private double fallStartY = Double.NaN;
    private long lastKindTick = Long.MIN_VALUE;
    private String lastKind;

    /**
     * Run one detection pass. Call once per server tick per companion; the body
     * argument must be the same companion every time.
     */
    public void tick(NumenPlayer body) {
        long now = body.level().getGameTime();
        for (Event ev : observe(now, body.isInWater(),
                body.getAirSupply() <= com.dwinovo.numen.task.BodySituation.AIR_LOW_TICKS,
                body.getHealth(), body.getY(), body.onGround(), body.getAirSupply())) {
            fire(body, now, ev.kind(), ev.data());
        }
    }

    /**
     * Pure detection pass over raw observations — no Minecraft objects, so the
     * edge detection (water/air/hp/fall) is headless-testable. Returns the
     * events detected on this pass, oldest first; the caller applies debounce
     * and delivery. State (previous tick's situation, fall start) is advanced
     * regardless of whether an event fires.
     */
    List<Event> observe(long now, boolean inWater, boolean airLow, double hp,
                        double y, boolean onGround, int air) {
        List<Event> out = new ArrayList<>();

        if (inWater && !wasInWater) {
            out.add(new Event(GameEvents.Kind.ENTERED_WATER, Map.of()));
        } else if (!inWater && wasInWater) {
            out.add(new Event(GameEvents.Kind.LEFT_WATER, Map.of()));
        }

        if (airLow && !wasAirLow) {
            out.add(new Event(GameEvents.Kind.AIR_LOW, Map.of("air", air)));
        }

        if (!Double.isNaN(lastHp) && hp < lastHp - DAMAGE_EVENT_THRESHOLD) {
            out.add(new Event(GameEvents.Kind.DAMAGED, Map.of(
                    "from_hp", lastHp, "to_hp", hp)));
        }

        if (!onGround) {
            if (lastOnGround) {
                fallStartY = y;   // just left the ground — start measuring the fall
            } else if (!Double.isNaN(fallStartY) && y < fallStartY - FALL_EVENT_THRESHOLD) {
                out.add(new Event(GameEvents.Kind.FELL, Map.of(
                        "fell_blocks", Math.round((fallStartY - y) * 10) / 10.0)));
                fallStartY = Double.NaN;   // one event per fall episode
            }
        } else {
            fallStartY = Double.NaN;   // landed — a fresh fall can start later
        }

        wasInWater = inWater;
        wasAirLow = airLow;
        lastHp = hp;
        lastOnGround = onGround;
        return out;
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
