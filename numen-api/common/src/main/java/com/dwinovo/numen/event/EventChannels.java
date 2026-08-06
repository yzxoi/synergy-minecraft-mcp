package com.dwinovo.numen.event;

import com.dwinovo.numen.entity.NumenPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-companion event rings — the external-brain event channel backing the
 * {@code get_events} MCP tool. {@link GameEvents} (task_finished, body_log,
 * dimension_change) and the situation-change detector both append here; the
 * MCP tool reads from the ring.
 *
 * <p>The ring is a pure observation window: bounded, in-memory, per-companion,
 * monotonically sequenced. It deliberately does NOT duplicate the built-in
 * brain's inbox (which rides the owner's client) — this channel is only for
 * drivers that have no client inbox, i.e. external MCP callers.
 *
 * <p>Threading: written from the server tick thread, read from HTTP threads;
 * {@link EventRingBuffer} synchronizes internally.
 */
public final class EventChannels {

    private static final Map<UUID, EventRingBuffer> RINGS = new HashMap<>();
    private static final Object LOCK = new Object();

    private EventChannels() {}

    /** The ring for a companion, creating it on first use. */
    public static EventRingBuffer ringFor(NumenPlayer body) {
        synchronized (LOCK) {
            return RINGS.computeIfAbsent(body.getUUID(), k -> new EventRingBuffer());
        }
    }

    /** Ring lookup without creating — null when this companion has no events yet. */
    public static EventRingBuffer ringOrNull(UUID companionUuid) {
        synchronized (LOCK) {
            return RINGS.get(companionUuid);
        }
    }

    /** Drop a companion's ring (despawn / dismissal / death cleanup). */
    public static void drop(UUID companionUuid) {
        synchronized (LOCK) {
            RINGS.remove(companionUuid);
        }
    }

    /** Append one event to a companion's ring; no-op when the body is null. */
    public static void append(NumenPlayer body, String kind, Map<String, Object> data) {
        if (body == null) return;
        long ts = body.level() == null ? 0L : body.level().getGameTime();
        ringFor(body).append(kind, ts, data);
    }
}
